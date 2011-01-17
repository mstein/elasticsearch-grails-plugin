/*
 * Copyright 2002-2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.plugins.elasticsearch.util

import org.codehaus.groovy.grails.commons.GrailsDomainClass
import org.grails.plugins.elasticsearch.ElasticSearchContextHolder
import org.grails.plugins.elasticsearch.ElasticSearchHelper
import org.grails.plugins.elasticsearch.mapping.ClosureSearchableDomainClassMapper
import org.grails.plugins.elasticsearch.mapping.ElasticSearchMappingFactory
import org.elasticsearch.client.Client
import org.elasticsearch.indices.IndexAlreadyExistsException
import org.elasticsearch.transport.RemoteTransportException
import org.elasticsearch.client.Requests
import org.apache.commons.logging.LogFactory
import org.grails.plugins.elasticsearch.mapping.SearchableClassMapping

class DomainDynamicMethodsUtils {

    static LOG = LogFactory.getLog("org.grails.plugins.elasticSearch.DomainDynamicMethodsUtils")


    /**
     * Inject the dynamic methods in the searchable domain classes.
     * Consider that the mapping has been resolve beforehand.
     * @param domainClasses
     * @param grailsApplication
     * @param applicationContext
     * @return
     */
    static injectDynamicMethods(domainClasses, grailsApplication, applicationContext) {
        def elasticSearchService = applicationContext.getBean("elasticSearchService")
        def elasticSearchContextHolder = applicationContext.getBean(ElasticSearchContextHolder)

        for (GrailsDomainClass domain in grailsApplication.domainClasses) {
            if (domain.getPropertyValue("searchable")) {
                def domainCopy = domain
                // Only inject the search method if the domain is mapped as "root"
                if (elasticSearchContextHolder.getMappingContext(domainCopy)?.root) {
                    domain.metaClass.static.search = { String q, Map params = [indices: domainCopy.packageName ?: domainCopy.propertyName, types: domainCopy.propertyName] ->
                        elasticSearchService.search(q, params)
                    }
                }
            }
        }
    }
}

/*
 * Copyright 2002-2011 the original author or authors.
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

import grails.util.Environment
import grails.util.GrailsUtil

import org.apache.log4j.Logger
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.orm.hibernate.HibernateEventListeners
import org.grails.plugins.elasticsearch.AuditEventListener
import org.grails.plugins.elasticsearch.ClientNodeFactoryBean
import org.grails.plugins.elasticsearch.ElasticSearchContextHolder
import org.grails.plugins.elasticsearch.ElasticSearchHelper
import org.grails.plugins.elasticsearch.conversion.CustomEditorRegistar
import org.grails.plugins.elasticsearch.conversion.JSONDomainFactory
import org.grails.plugins.elasticsearch.conversion.unmarshall.DomainClassUnmarshaller
import org.grails.plugins.elasticsearch.index.IndexRequestQueue
import org.grails.plugins.elasticsearch.mapping.SearchableClassMappingConfigurator
import org.grails.plugins.elasticsearch.util.DomainDynamicMethodsUtils
import org.springframework.context.ApplicationContext

class ElasticsearchGrailsPlugin {

    static LOG = Logger.getLogger("org.grails.plugins.elasticsearch.ElasticsearchGrailsPlugin")

    def version = "0.20.6.0-SNAPSHOT"
    def grailsVersion = "1.3.0 > *"
    def loadAfter = ['services']
    def pluginExcludes = [
        "grails-app/views/error.gsp",
        "grails-app/controllers/test/**",
        "grails-app/services/test/**",
        "grails-app/views/elasticSearch/index.gsp",
        "grails-app/domain/test/**",
        "grails-app/utils/test/**",
        "test/**",
        "src/docs/**"
    ]

    def license = "APACHE"
    def organization = [name: "doc4web", url: "http://www.doc4web.com/"]
    def developers = [
        [name: "Manuarii Stein", email: "mstein@doc4web.com"],
        [name: "Stéphane Maldini", email: "smaldini@doc4web.com"]
    ]

    def issueManagement = [system: "icescrum", url: "http://doc4web.com/icescrum/p/ELASTIC#project"]
    def scm = [url: "https://github.com/mstein/elasticsearch-grails-plugin"]

    def author = "Manuarii Stein"
    def authorEmail = "mstein@doc4web.com"
    def title = "Elastic Search Plugin"
    def description = """\
[Elastic Search|http://www.elasticsearch.com] is a distributed, RESTful service for full text search. This plugin provides a Grails-friendly API for the service based on the tremendously successful [Searchable plugin|/plugin/searchable]. It even provides an embedded version of the service for easy testing and development."""

    def documentation = "http://smaldini.github.com/elasticsearch-grails-plugin/docs/guide/index.html"

    def doWithSpring = {
        def esConfig = getConfiguration(parentCtx, application)

        elasticSearchContextHolder(ElasticSearchContextHolder) {
            config = esConfig
        }

        elasticSearchHelper(ElasticSearchHelper) {
            elasticSearchClient = ref("elasticSearchClient")
        }

        elasticSearchClient(ClientNodeFactoryBean) { bean ->
            elasticSearchContextHolder = ref("elasticSearchContextHolder")
            bean.destroyMethod = 'shutdown'
        }

        indexRequestQueue(IndexRequestQueue) {
            elasticSearchContextHolder = ref("elasticSearchContextHolder")
            elasticSearchClient = ref("elasticSearchClient")
            jsonDomainFactory = ref("jsonDomainFactory")
            sessionFactory = ref("sessionFactory")
        }

        searchableClassMappingConfigurator(SearchableClassMappingConfigurator) { bean ->
            elasticSearchContext = ref("elasticSearchContextHolder")
            grailsApplication = ref("grailsApplication")
            elasticSearchClient = ref("elasticSearchClient")
            config = esConfig
            bean.initMethod = 'configureAndInstallMappings'
        }

        domainInstancesRebuilder(DomainClassUnmarshaller) {
            elasticSearchContextHolder = ref("elasticSearchContextHolder")
            elasticSearchClient = ref("elasticSearchClient")
            grailsApplication = ref("grailsApplication")
        }

        customEditorRegistrar(CustomEditorRegistar) {
            grailsApplication = ref("grailsApplication")
        }

        jsonDomainFactory(JSONDomainFactory) {
            elasticSearchContextHolder = ref("elasticSearchContextHolder")
            grailsApplication = ref("grailsApplication")
        }

        auditListener(AuditEventListener) {
            elasticSearchContextHolder = ref("elasticSearchContextHolder")
        }

        if (!esConfig.disableAutoIndex) {
            // do not install audit listener if auto-indexing is disabled.
            hibernateEventListeners(HibernateEventListeners) {
                listenerMap = [
                    'post-delete': auditListener,
                    'post-collection-update': auditListener,
//                    'save-update': auditListener,
                    'post-update': auditListener,
                    'post-insert': auditListener,
                    'flush': auditListener
                ]
            }
        }
    }

    def doWithDynamicMethods = { ctx ->
        // Define the custom ElasticSearch mapping for searchable domain classes
        DomainDynamicMethodsUtils.injectDynamicMethods(application.domainClasses, application, ctx)
    }

    // Get a configuration instance

    private getConfiguration(ApplicationContext applicationContext, GrailsApplication application) {
        def config = application.config
        // try to load it from class file and merge into GrailsApplication#config
        // Config.groovy properties override the default one
        try {
            Class dataSourceClass = application.getClassLoader().loadClass("DefaultElasticSearch")
            ConfigSlurper configSlurper = new ConfigSlurper(Environment.current.name)
            Map binding = [:]
            binding.userHome = System.properties['user.home']
            binding.grailsEnv = application.metadata["grails.env"]
            binding.appName = application.metadata["app.name"]
            binding.appVersion = application.metadata["app.version"]
            configSlurper.binding = binding
            def defaultConfig = configSlurper.parse(dataSourceClass)
            config = defaultConfig.merge(config)
            return config.elasticSearch
        }
        catch (ClassNotFoundException e) {
            LOG.debug("Not found: ${e.message}")
        }

        // try to get it from GrailsApplication#config
        if (config.containsKey("elasticSearch")) {
            if (!config.elasticSearch.date?.formats) {
                config.elasticSearch.date.formats = ["yyyy-MM-dd'T'HH:mm:ss'Z'"]
            }
            return config.elasticSearch
        }

        // No config found, add some default and obligatory properties
        ConfigSlurper configSlurper = new ConfigSlurper(GrailsUtil.getEnvironment())
        config.merge(configSlurper.parse({
            elasticSeatch {
                date.formats = ["yyyy-MM-dd'T'HH:mm:ss'Z'"]
            }
        }))

        return config
    }
}

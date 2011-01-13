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
package org.grails.plugins.elasticsearch.mapping

import org.codehaus.groovy.grails.commons.GrailsDomainClass
import grails.converters.JSON
import org.codehaus.groovy.grails.commons.DefaultGrailsDomainClassProperty

class ElasticSearchMappingFactory {

    static SUPPORTED_FORMAT = ['string', 'integer', 'long', 'float', 'double', 'boolean', 'null', 'date']

    static JSON getElasticMapping(SearchableClassMapping scm) {
        ElasticSearchMappingFactory.getElasticMapping(scm.domainClass, scm.propertiesMapping)
    }

    static JSON getElasticMapping(GrailsDomainClass domainClass, Collection<SearchableClassPropertyMapping> propertyMappings) {
        def properties = domainClass.getProperties()
        def mapBuilder = [
                (domainClass.propertyName): [
                        properties: [:]
                ]
        ]
        // Map each domain properties in supported format, or object for complex type
        properties.each {DefaultGrailsDomainClassProperty prop ->
            if (prop.name in propertyMappings*.propertyName) {
                def propType = prop.typePropertyName
                def propOptions = [:]
                // Add the custom mapping (searchable static property in domain model)
                def customMapping = propertyMappings.find {it.propertyName == prop.name}
                if (customMapping) {
                    customMapping.attributes.each { key, value ->
                        propOptions."${key}" = value
                    }
                }
                if (!(prop.typePropertyName in SUPPORTED_FORMAT)) {
                    // Use 'string' type for properties with custom converter.
                    if (customMapping.converter) {
                        propType = 'string'
                    } else {
                        propType = 'object'
                    }
                }
                propOptions.type = propType
                mapBuilder."${domainClass.propertyName}".properties << ["${prop.name}": propOptions]
            }
        }

        return mapBuilder as JSON
    }
}

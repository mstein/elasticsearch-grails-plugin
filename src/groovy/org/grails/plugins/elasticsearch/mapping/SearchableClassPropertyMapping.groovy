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

import org.codehaus.groovy.grails.commons.GrailsDomainClassProperty

class SearchableClassPropertyMapping {
    /** The name of the class property  */
    String propertyName
    /** The type of the class property  */
    Class propertyType
    /** Mapping attributes values, will be added in the ElasticSearch JSON mapping request  */
    Map attributes = [:]
    /** Special mapping attributes, only used by the plugin itself (eg: 'component', 'reference')  */
    Map specialAttributes = [:]

    public SearchableClassPropertyMapping(GrailsDomainClassProperty property) {
        this.propertyName = property.name
        this.propertyType = property.type
    }

    public SearchableClassPropertyMapping(GrailsDomainClassProperty property, Map options) {
        this.propertyName = property.name
        this.propertyType = property.type
        this.addAttributes(options)
    }

    public addAttributes(attributesMap) {
        attributesMap.each { key, value ->
            if (key in ClosureSearchableDomainClassMapper.SEARCHABLE_MAPPING_OPTIONS) {
                attributes += [(key): value]
            } else if (key in ClosureSearchableDomainClassMapper.SEARCHABLE_SPECIAL_MAPPING_OPTIONS) {
                specialAttributes += [(key): value]
            }
        }
    }

    public Boolean isComponent() {
        specialAttributes.any { k, v -> k == 'component' && v }
    }

    public def getConverter() {
        specialAttributes.converter
    }


    public String toString() {
        return "SearchableClassPropertyMapping{" +
                "propertyName='" + propertyName + '\'' +
                ", propertyType=" + propertyType +
                ", attributes=" + attributes +
                ", specialAttributes=" + specialAttributes +
                '}';
    }
}

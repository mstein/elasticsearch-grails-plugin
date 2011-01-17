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
import org.grails.plugins.elasticsearch.ElasticSearchContextHolder
import org.codehaus.groovy.grails.commons.GrailsDomainClass

class SearchableClassPropertyMapping {
    public static final SEARCHABLE_MAPPING_OPTIONS = ['boost','index']
    public static final SEARCHABLE_SPECIAL_MAPPING_OPTIONS = ['component','converter','reference']
    /** Grails attributes of this property */
    GrailsDomainClassProperty grailsProperty
    /** The name of the class property  */
    String propertyName
    /** The type of the class property  */
    Class propertyType
    /** Mapping attributes values, will be added in the ElasticSearch JSON mapping request  */
    Map attributes = [:]
    /** Special mapping attributes, only used by the plugin itself (eg: 'component', 'reference')  */
    Map specialAttributes = [:]

    public SearchableClassPropertyMapping(GrailsDomainClassProperty property) {
        this.grailsProperty = property
        this.propertyName = property.name
        this.propertyType = property.type
    }

    public SearchableClassPropertyMapping(GrailsDomainClassProperty property, Map options) {
        this.grailsProperty = property
        this.propertyName = property.name
        this.propertyType = property.type
        this.addAttributes(options)
    }

    public addAttributes(attributesMap) {
        attributesMap.each { key, value ->
            if (key in SEARCHABLE_MAPPING_OPTIONS) {
                attributes += [(key): value]
            } else if (key in SEARCHABLE_SPECIAL_MAPPING_OPTIONS) {
                specialAttributes += [(key): value]
            } else {
                throw new IllegalArgumentException("Invalid option ${key} found in searchable mapping.")
            }
        }
    }

    public Boolean isComponent() {
        specialAttributes.any { k, v -> k == 'component' && v }
    }

    public def getConverter() {
        specialAttributes.converter
    }

    public def getReference() {
        specialAttributes.reference
    }


    public Class getBestGuessReferenceType() {
        // is type defined explicitly?
        if (this.reference instanceof Class) {
            return (Class) this.reference
        }

        // is it association?
        if (this.grailsProperty.association) {
            return this.grailsProperty.referencedPropertyType
        }

        throw new IllegalStateException("Property ${propertyName} is not an association, cannot be defined as 'reference'")
    }

    /**
     * Validate searchable mappings for this property.
     */
    public void validate(ElasticSearchContextHolder contextHolder) {
        if (this.component && this.reference) {
            throw new IllegalArgumentException("Property ${propertyName} cannot be 'component' and 'reference' at once.")
        }
        // Are we referencing searchable class?
        if (this.reference) {
            Class myReferenceType = getBestGuessReferenceType();
            // Compare using exact match of classes.
            // May not be correct to inheritance model.
            SearchableClassMapping scm = contextHolder.mapping.values().find { it.domainClass.clazz == myReferenceType }
            if (!scm) {
                throw new IllegalArgumentException("Property ${propertyName} declared as reference to non-searchable class ${myReferenceType}")
            }
            // Should it be a root class????
            if (!scm.root) {
                throw new IllegalArgumentException("Property ${propertyName} declared as reference to non-root class ${myReferenceType}")
            }
        }
    }

    /**
     * @return searchable property mapping information.
     */
    public String toString() {
        return "SearchableClassPropertyMapping{" +
                "propertyName='" + propertyName + '\'' +
                ", propertyType=" + propertyType +
                ", attributes=" + attributes +
                ", specialAttributes=" + specialAttributes +
                '}';
    }
}

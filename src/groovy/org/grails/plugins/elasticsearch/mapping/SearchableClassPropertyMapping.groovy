/*
 * Copyright 2002-2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

/**
 * Custom searchable property mapping.
 */
class SearchableClassPropertyMapping {

    private static final Set<String> SEARCHABLE_MAPPING_OPTIONS = ['boost', 'index', 'analyzer']
    private static final Set<String> SEARCHABLE_SPECIAL_MAPPING_OPTIONS =
            ['component', 'converter', 'reference', 'excludeFromAll', 'maxDepth', 'multi_field', 'parent', 'geoPoint',
             'alias']

    /** Grails attributes of this property */
    private GrailsDomainClassProperty grailsProperty

    /** Mapping attributes values, will be added in the ElasticSearch JSON mapping request  */
    private Map<String, Object> mappingAttributes = [:]

    /** Special mapping attributes, only used by the plugin itself (eg: 'component', 'reference')  */
    private Map<String, Object> specialMappingAttributes = [:]

    private SearchableClassMapping componentPropertyMapping

    SearchableClassPropertyMapping(GrailsDomainClassProperty property) {
        grailsProperty = property
    }

    SearchableClassPropertyMapping(GrailsDomainClassProperty property, Map options) {
        grailsProperty = property
        addAttributes(options)
    }

    void addAttributes(Map<String, Object> attributesMap) {
        attributesMap.each { key, value ->
            if (SEARCHABLE_MAPPING_OPTIONS.contains(key)) {
                mappingAttributes.put(key, value)
            } else if (SEARCHABLE_SPECIAL_MAPPING_OPTIONS.contains(key)) {
                specialMappingAttributes.put(key, value)
            } else {
                throw new IllegalArgumentException("Invalid option $key found in searchable mapping.")
            }
        }
    }

    /**
     * @return component property?
     */
    boolean isComponent() {
        specialMappingAttributes.component != null
    }

    Object getConverter() {
        specialMappingAttributes.converter
    }

    Object getReference() {
        specialMappingAttributes.reference
    }

    boolean isMultiField() {
        specialMappingAttributes.multi_field != null
    }

    boolean isParent() {
        Object parentVal = specialMappingAttributes.parent
        (parentVal != null) && ((Boolean) parentVal)
    }

    /**
     * See http://www.elasticsearch.com/docs/elasticsearch/mapping/all_field/
     * @return exclude this property from ALL aggregate field?
     */
    boolean shouldExcludeFromAll() {
        Object excludeFromAll = specialMappingAttributes.excludeFromAll
        if (excludeFromAll == null) {
            return false
        }
        if (excludeFromAll instanceof Boolean) {
            return (Boolean) excludeFromAll
        }
        // introduce behaviour compatible with Searchable Plugin.
        return excludeFromAll.toString().equalsIgnoreCase('yes')
    }

    int getMaxDepth() {
        Object maxDepth = specialMappingAttributes.maxDepth
        maxDepth != null ? maxDepth : 0 as int
    }

    Class getBestGuessReferenceType() {
        // is type defined explicitly?
        if (reference instanceof Class) {
            return reference as Class
        }

        // is it association?
        if (grailsProperty.association) {
            return grailsProperty.referencedPropertyType
        }

        throw new IllegalStateException("Property $propertyName is not an association, cannot be defined as 'reference'")
    }

    /**
     * Validate searchable mappings for this property.
     * NOTE: We can leave the validation of the options from SEARCHABLE_MAPPING_OPTIONS to ElasticSearch
     * as it will throw an error if a mapping value is invalid.
     */
    void validate(ElasticSearchContextHolder contextHolder) {
        if (component && (reference != null)) {
            throw new IllegalArgumentException("Property ${grailsProperty.name} cannot be 'component' and 'reference' at once.")
        }

        if (component && (componentPropertyMapping == null)) {
            throw new IllegalArgumentException("Property ${grailsProperty.name} is mapped as component, but dependent mapping is not injected.")
        }

        // Are we referencing searchable class?
        if (reference != null) {
            Class myReferenceType = bestGuessReferenceType
            // Compare using exact match of classes.
            // May not be correct to inheritance model.
            SearchableClassMapping scm = contextHolder.getMappingContextByType(myReferenceType)
            if (scm == null) {
                throw new IllegalArgumentException("Property ${grailsProperty.name} declared as reference to non-searchable class $myReferenceType")
            }
            // Should it be a root class????
            if (!scm.isRoot()) {
                throw new IllegalArgumentException("Property ${grailsProperty.name} declared as reference to non-root class $myReferenceType")
            }
        }
    }

    /**
     * @return searchable property mapping information.
     */
    String toString() {
        "SearchableClassPropertyMapping{propertyName=$propertyName, propertyType='$propertyType, mappingAttributes=$mappingAttributes, specialMappingAttributes=$specialMappingAttributes"
    }

    private Class<?> getPropertyType() {
        grailsProperty.type
    }

    String getPropertyName() {
        grailsProperty.name
    }

    GrailsDomainClassProperty getGrailsProperty() {
        return grailsProperty
    }

    Map<String, Object> getAttributes() {
        Collections.unmodifiableMap(mappingAttributes)
    }

    SearchableClassMapping getComponentPropertyMapping() {
        componentPropertyMapping
    }

    void setComponentPropertyMapping(SearchableClassMapping componentPropertyMapping) {
        this.componentPropertyMapping = componentPropertyMapping
    }

    /**
     * @return true if field is analyzed. NOTE it doesn't have to be stored.
     */
    boolean isAnalyzed() {
        String index = (String) mappingAttributes.index
        (index == null || index == 'analyzed')
    }

    /**
     * True if property is a variant of geo_point type
     */
    boolean isGeoPoint() {
        Object geoPoint = specialMappingAttributes.get('geoPoint')
        (geoPoint != null && ((Boolean) geoPoint))
    }

	boolean isAlias(){
        getAlias();
	}
	
	String getAlias(){
		specialMappingAttributes.get('alias')
	}
}

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
public class SearchableClassPropertyMapping {

    private static final Set<String> SEARCHABLE_MAPPING_OPTIONS = ['boost', 'index', 'analyzer'] as Set<String>
    private static final Set<String> SEARCHABLE_SPECIAL_MAPPING_OPTIONS =
        ['component', 'converter', 'reference', 'excludeFromAll', 'maxDepth', 'multi_field', 'parent'] as Set<String>

    /** Grails attributes of this property */
    private GrailsDomainClassProperty grailsProperty
    /** Mapping attributes values, will be added in the ElasticSearch JSON mapping request  */
    private Map<String, Object> mappingAttributes = [:]
    /** Special mapping attributes, only used by the plugin itself (eg: 'component', 'reference')  */
    private Map<String, Object> specialMappingAttributes = [:]

    private SearchableClassMapping componentPropertyMapping

    public SearchableClassPropertyMapping(GrailsDomainClassProperty property) {
        this.grailsProperty = property
    }

    public SearchableClassPropertyMapping(GrailsDomainClassProperty property, Map options) {
        this.grailsProperty = property
        this.addAttributes(options)
    }

    public void addAttributes(Map<String, Object> attributesMap) {
        attributesMap.each { key, value ->
            if (SEARCHABLE_MAPPING_OPTIONS.contains(key)) {
                this.mappingAttributes.put(key, value)
            } else if (SEARCHABLE_SPECIAL_MAPPING_OPTIONS.contains(key)) {
                this.specialMappingAttributes.put(key, value)
            } else {
                throw new IllegalArgumentException("Invalid option $key found in searchable mapping.")
            }
        }
    }

    /**
     * @return component property?
     */
    public boolean isComponent() {
        specialMappingAttributes.component != null
    }

    public Object getConverter() {
        specialMappingAttributes.converter
    }

    public Object getReference() {
        specialMappingAttributes.reference
    }

    public boolean isMultiField() {
        specialMappingAttributes.multi_field != null
    }

    public boolean isParent() {
        Object parentVal = specialMappingAttributes.parent
        (parentVal != null) && ((Boolean) parentVal)
    }

    /**
     * See http://www.elasticsearch.com/docs/elasticsearch/mapping/all_field/
     * @return exclude this property from ALL aggregate field?
     */
    public boolean shouldExcludeFromAll() {
        Object excludeFromAll = specialMappingAttributes.excludeFromAll
        if (excludeFromAll == null) {
            return false
        } else if (excludeFromAll instanceof Boolean) {
            return (Boolean) excludeFromAll
        } else {
            // introduce behaviour compatible with Searchable Plugin.
            return excludeFromAll.toString().equalsIgnoreCase('yes')
        }
    }

    public int getMaxDepth() {
        Object maxDepth = specialMappingAttributes.maxDepth
        maxDepth != null ? (Integer) maxDepth : 0
    }

    public Class getBestGuessReferenceType() {
        // is type defined explicitly?
        if (getReference() instanceof Class) {
            return (Class) this.getReference()
        }

        // is it association?
        if (this.grailsProperty.isAssociation()) {
            return this.grailsProperty.getReferencedPropertyType()
        }

        throw new IllegalStateException("Property ${getPropertyName()} is not an association, cannot be defined as 'reference'")
    }

    /**
     * Validate searchable mappings for this property.
     * NOTE: We can leave the validation of the options from SEARCHABLE_MAPPING_OPTIONS to ElasticSearch
     * as it will throw an error if a mapping value is invalid.
     */
    public void validate(ElasticSearchContextHolder contextHolder) {
        if (isComponent() && (getReference() != null)) {
            throw new IllegalArgumentException("Property ${grailsProperty.getName()} cannot be 'component' and 'reference' at once.")
        }

        if (isComponent() && (componentPropertyMapping == null)) {
            throw new IllegalArgumentException("Property ${grailsProperty.getName()} is mapped as component, but dependent mapping is not injected.")
        }

        // Are we referencing searchable class?
        if (getReference() != null) {
            Class myReferenceType = getBestGuessReferenceType()
            // Compare using exact match of classes.
            // May not be correct to inheritance model.
            SearchableClassMapping scm = contextHolder.getMappingContextByType(myReferenceType)
            if (scm == null) {
                throw new IllegalArgumentException("Property ${grailsProperty.getName()} declared as reference to non-searchable class $myReferenceType")
            }
            // Should it be a root class????
            if (!scm.isRoot()) {
                throw new IllegalArgumentException("Property ${grailsProperty.getName()} declared as reference to non-root class $myReferenceType")
            }
        }
    }

    /**
     * @return searchable property mapping information.
     */
    public String toString() {
        "SearchableClassPropertyMapping{propertyName=${getPropertyName()}, propertyType='${getPropertyType()}, " +
                "mappingAttributes=$mappingAttributes, specialMappingAttributes=$specialMappingAttributes"
    }

    private Class<?> getPropertyType() {
        grailsProperty.getType()
    }

    public String getPropertyName() {
        grailsProperty.getName()
    }

    public GrailsDomainClassProperty getGrailsProperty() {
        return grailsProperty
    }

    public Map<String, Object> getAttributes() {
        Collections.unmodifiableMap(mappingAttributes)
    }

    public SearchableClassMapping getComponentPropertyMapping() {
        componentPropertyMapping
    }

    void setComponentPropertyMapping(SearchableClassMapping componentPropertyMapping) {
        this.componentPropertyMapping = componentPropertyMapping
    }

    /**
     * @return true if field is analyzed. NOTE it doesn't have to be stored.
     */
    public boolean isAnalyzed() {
        String index = (String) mappingAttributes.index
        (index == null || index == 'analyzed')
    }
}

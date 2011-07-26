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
package org.grails.plugins.elasticsearch.mapping;

import org.codehaus.groovy.grails.commons.GrailsDomainClassProperty;
import org.grails.plugins.elasticsearch.ElasticSearchContextHolder;

import java.util.*;

/**
 * Custom searchable property mapping.
 */
public class SearchableClassPropertyMapping {

    public static final Set<String> SEARCHABLE_MAPPING_OPTIONS = new HashSet<String>(Arrays.asList("boost", "index", "analyzer"));
    public static final Set<String> SEARCHABLE_SPECIAL_MAPPING_OPTIONS = new HashSet<String>(Arrays.asList("component","converter","reference","excludeFromAll","maxDepth"));

    /** Grails attributes of this property */
    GrailsDomainClassProperty grailsProperty;
    /** Mapping attributes values, will be added in the ElasticSearch JSON mapping request  */
    Map<String, Object> attributes = new HashMap<String, Object>();
    /** Special mapping attributes, only used by the plugin itself (eg: 'component', 'reference')  */
    Map<String, Object> specialAttributes = new HashMap<String, Object>();

    public SearchableClassPropertyMapping(GrailsDomainClassProperty property) {
        this.grailsProperty = property;
    }

    public SearchableClassPropertyMapping(GrailsDomainClassProperty property, Map options) {
        this.grailsProperty = property;
        this.addAttributes(options);
    }

    public void addAttributes(Map<String, Object> attributesMap) {
        for(Map.Entry<String, Object> entry : attributesMap.entrySet()) {
            if (SEARCHABLE_MAPPING_OPTIONS.contains(entry.getKey())) {
                attributes.put(entry.getKey(), entry.getValue());
            } else if (SEARCHABLE_SPECIAL_MAPPING_OPTIONS.contains(entry.getKey())) {
                specialAttributes.put(entry.getKey(), entry.getValue());
            } else {
                throw new IllegalArgumentException("Invalid option " + entry.getKey() + " found in searchable mapping.");
            }
        }
    }

    /**
     * @return component property?
     */
    public boolean isComponent() {
        return specialAttributes.get("component") != null;
    }

    public Object getConverter() {
        return specialAttributes.get("converter");
    }

    public Object getReference() {
        return specialAttributes.get("reference");
    }

    /**
     * See http://www.elasticsearch.com/docs/elasticsearch/mapping/all_field/
     * @return exclude this property from ALL aggregate field?
     */
    public boolean shouldExcludeFromAll() {
        Object excludeFromAll = specialAttributes.get("excludeFromAll");
        if (excludeFromAll == null) {
            return false;
        } else if (excludeFromAll instanceof Boolean) {
            return (Boolean) excludeFromAll;
        } else {
            // introduce behaviour compatible with Searchable Plugin.
            return excludeFromAll.toString().equalsIgnoreCase("yes");
        }
    }

    public int getMaxDepth() {
        Object maxDepth = specialAttributes.get("maxDepth");
        if (maxDepth != null) {
            return (Integer)maxDepth;
        } else {
            return 0;
        }
    }



    public Class getBestGuessReferenceType() {
        // is type defined explicitly?
        if (getReference() instanceof Class) {
            return (Class) this.getReference();
        }

        // is it association?
        if (this.grailsProperty.isAssociation()) {
            return this.grailsProperty.getReferencedPropertyType();
        }

        throw new IllegalStateException("Property " + getPropertyName() + " is not an association, cannot be defined as 'reference'");
    }

    /**
     * Validate searchable mappings for this property.
     * NOTE: We can leave the validation of the options from SEARCHABLE_MAPPING_OPTIONS to ElasticSearch
     * as it will throw an error if a mapping value is invalid.
     */
    public void validate(ElasticSearchContextHolder contextHolder) {
        if (this.isComponent() && this.getReference() != null) {
            throw new IllegalArgumentException("Property " + grailsProperty.getName() + " cannot be 'component' and 'reference' at once.");
        }

        if (this.isComponent() && this.componentPropertyMapping == null) {
            throw new IllegalArgumentException("Property " + grailsProperty.getName() + " is mapped as component, but dependent mapping is not injected.");
        }

        // Are we referencing searchable class?
        if (this.getReference() != null) {
            Class myReferenceType = getBestGuessReferenceType();
            // Compare using exact match of classes.
            // May not be correct to inheritance model.
            SearchableClassMapping scm = contextHolder.getMappingContextByType(myReferenceType);
            if (scm == null) {
                throw new IllegalArgumentException("Property " + grailsProperty.getName() + " declared as reference to non-searchable class " + myReferenceType);
            }
            // Should it be a root class????
            if (!scm.isRoot()) {
                throw new IllegalArgumentException("Property " + grailsProperty.getName() + " declared as reference to non-root class " + myReferenceType);
            }
        }
    }

    /**
     * @return searchable property mapping information.
     */
    public String toString() {
        return "SearchableClassPropertyMapping{" +
                "propertyName='" + getPropertyName() + '\'' +
                ", propertyType=" + getPropertyType() +
                ", attributes=" + attributes +
                ", specialAttributes=" + specialAttributes +
                '}';
    }

    private Class<?> getPropertyType() {
        return grailsProperty.getType();
    }

    public String getPropertyName() {
        return grailsProperty.getName();
    }

    public GrailsDomainClassProperty getGrailsProperty() {
        return grailsProperty;
    }

    public Map<String,Object> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    private SearchableClassMapping componentPropertyMapping;

    public SearchableClassMapping getComponentPropertyMapping() {
        return componentPropertyMapping;
    }

    void setComponentPropertyMapping(SearchableClassMapping componentPropertyMapping) {
        this.componentPropertyMapping = componentPropertyMapping;
    }

    /**
     * @return true if field is analyzed. NOTE it doesn't have to be stored.
     */
    public boolean isAnalyzed() {
        String index = (String) attributes.get("index");
        return (index == null || index.equals("analyzed"));
    }
}

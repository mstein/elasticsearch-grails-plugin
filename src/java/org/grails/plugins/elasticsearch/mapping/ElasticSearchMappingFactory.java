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

import org.codehaus.groovy.grails.commons.GrailsClassUtils;
import org.springframework.util.ClassUtils;

import java.util.*;

/**
 * Build ElasticSearch class mapping based on attributes provided by closure.
 */
public class ElasticSearchMappingFactory {

    private static final Set<String> SUPPORTED_FORMAT = new HashSet<String>(Arrays.asList(
            "string", "integer", "long", "float", "double", "boolean", "null", "date"));

    private static Class JODA_TIME_BASE;

    static {
        try {
            JODA_TIME_BASE = Class.forName("org.joda.time.ReadableInstant");
        } catch (ClassNotFoundException e) { }
    }

    public static Map<String, Object> getElasticMapping(SearchableClassMapping scm) {
        Map<String, Object> elasticTypeMappingProperties = new LinkedHashMap<String, Object>();

        if (!scm.isAll()) {
            // "_all" : {"enabled" : true}
            elasticTypeMappingProperties.put("_all",
                Collections.singletonMap("enabled", false));
        }

        // Map each domain properties in supported format, or object for complex type
        for(SearchableClassPropertyMapping scpm : scm.getPropertiesMapping()) {
            // Does it have custom mapping?
            String propType = scpm.getGrailsProperty().getTypePropertyName();
            Map<String, Object> propOptions = new LinkedHashMap<String, Object>();
            // Add the custom mapping (searchable static property in domain model)
            propOptions.putAll(scpm.getAttributes());
            if (!(SUPPORTED_FORMAT.contains(scpm.getGrailsProperty().getTypePropertyName()))) {
                // Handle embedded persistent collections, ie List<String> listOfThings
                if (scpm.getGrailsProperty().isBasicCollectionType()) {
                    String basicType = ClassUtils.getShortName(scpm.getGrailsProperty().getReferencedPropertyType()).toLowerCase(Locale.ENGLISH);
                    if (SUPPORTED_FORMAT.contains(basicType)) {
                        propType = basicType;
                    }
                // Handle arrays
                } else if (scpm.getGrailsProperty().getReferencedPropertyType().isArray()) {
                    String basicType = ClassUtils.getShortName(scpm.getGrailsProperty().getReferencedPropertyType().getComponentType()).toLowerCase(Locale.ENGLISH);
                    if (SUPPORTED_FORMAT.contains(basicType)) {
                        propType = basicType;
                    }
                } else if (isDateType(scpm.getGrailsProperty().getReferencedPropertyType())) {
                    propType = "date";
                } else if (GrailsClassUtils.isJdk5Enum(scpm.getGrailsProperty().getReferencedPropertyType())) {
                    propType = "string";
                } else if (scpm.getConverter() != null) {
                    // Use 'string' type for properties with custom converter.
                    // Arrays are automatically resolved by ElasticSearch, so no worries.
                    propType = "string";
                } else {
                    propType = "object";
                }

                if (scpm.getReference() != null) {
                    propType = "object";      // fixme: think about composite ids.
                } else if (scpm.isComponent()) {
                    // Proceed with nested mapping.
                    // todo limit depth to avoid endless recursion?
                    propType = "object";
                    //noinspection unchecked
                    propOptions.putAll((Map<String, Object>)
                            (getElasticMapping(scpm.getComponentPropertyMapping()).values().iterator().next()));

                }

                // Once it is an object, we need to add id & class mappings, otherwise
                // ES will fail with NullPointer.
                if (scpm.isComponent() || scpm.getReference() != null) {
                    @SuppressWarnings({"unchecked"})
                    Map<String, Object> props = (Map<String, Object>) propOptions.get("properties");
                    if (props == null) {
                        props = new LinkedHashMap<String, Object>();
                        propOptions.put("properties", props);
                    }
                    props.put("id", defaultDescriptor("long", "not_analyzed", true));
                    props.put("class", defaultDescriptor("string", "no", true));
                    props.put("ref", defaultDescriptor("string", "no", true));
                }
            }
            propOptions.put("type", propType);
            // See http://www.elasticsearch.com/docs/elasticsearch/mapping/all_field/
            if (!propType.equals("object") && scm.isAll()) {
                // does it make sense to include objects into _all?
                if (scpm.shouldExcludeFromAll()) {
                    propOptions.put("include_in_all", false);
                } else {
                    propOptions.put("include_in_all", true);
                }
            }
            // todo only enable this through configuration...
            if (propType.equals("string") && scpm.isAnalyzed()) {
                propOptions.put("term_vector", "with_positions_offsets");
            }
            elasticTypeMappingProperties.put(scpm.getPropertyName(), propOptions);
        }

        Map<String, Object> mapping = new LinkedHashMap<String, Object>();
        mapping.put(scm.getElasticTypeName(),
                Collections.singletonMap("properties", elasticTypeMappingProperties));

        return mapping;
    }

    private static boolean isDateType(Class type) {
        return (JODA_TIME_BASE != null && JODA_TIME_BASE.isAssignableFrom(type)) || java.util.Date.class.isAssignableFrom(type);
    }

    private static Map<String, Object> defaultDescriptor(String type, String index, boolean excludeFromAll) {
        Map<String, Object> props = new LinkedHashMap<String, Object>();
        props.put("type", type);
        props.put("index", index);
        props.put("include_in_all", !excludeFromAll);
        return props;
    }

}

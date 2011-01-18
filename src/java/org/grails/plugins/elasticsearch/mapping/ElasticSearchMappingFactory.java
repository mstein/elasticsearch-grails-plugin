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

import java.util.*;

/**
 * Build ElasticSearch class mapping based on attributes provided by closure.
 */
public class ElasticSearchMappingFactory {

    private static final Set<String> SUPPORTED_FORMAT = new HashSet<String>(Arrays.asList(
            "string", "integer", "long", "float", "double", "boolean", "null", "date"));

    public static Map<String, Object> getElasticMapping(SearchableClassMapping scm) {
        Map<String, Object> elasticTypeMappingProperties = new LinkedHashMap<String, Object>();

        if (!scm.isAll()) {
            // "_all" : {"enabled" : true}
            elasticTypeMappingProperties.put("_all",
                Collections.singletonMap("enabled", false));
        }

        // Map each domain properties in supported format, or object for complex type
        for(GrailsDomainClassProperty prop : scm.getDomainClass().getProperties()) {
            // Does it have custom mapping?
            SearchableClassPropertyMapping scpm = scm.getPropertyMapping(prop.getName());
            if (scpm != null) {
                String propType = prop.getTypePropertyName();
                Map<String, Object> propOptions = new LinkedHashMap<String, Object>();
                // Add the custom mapping (searchable static property in domain model)
                propOptions.putAll(scpm.getAttributes());
                if (!(SUPPORTED_FORMAT.contains(prop.getTypePropertyName()))) {
                    // Use 'string' type for properties with custom converter.
                    // Arrays are automatically resolved by ElasticSearch, so no worries.
                    if (scpm.getConverter() != null) {
                        propType = "string";
                    } else {
                        propType = "object";
                    }

                    if (scpm.getReference() != null) {
                        propType = "long";      // fixme: think about composite ids.
                    }
                }
                propOptions.put("type", propType);
                // See http://www.elasticsearch.com/docs/elasticsearch/mapping/all_field/
                if (scm.isAll()) {
                    if (scpm.shouldExcludeFromAll()) {
                        propOptions.put("include_in_all", false);
                    } else {
                        propOptions.put("include_in_all", true);
                    }
                }
                elasticTypeMappingProperties.put(prop.getName(), propOptions);
            }
        }

        Map<String, Object> mapping = new LinkedHashMap<String, Object>();
        mapping.put(scm.getDomainClass().getPropertyName(),
                Collections.singletonMap("properties", elasticTypeMappingProperties));

        return mapping;
    }

}

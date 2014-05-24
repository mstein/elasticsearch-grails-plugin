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

package org.grails.plugins.elasticsearch.mapping;

import org.codehaus.groovy.grails.commons.GrailsDomainClass;
import org.grails.plugins.elasticsearch.ElasticSearchContextHolder;

public class SearchableClassMapping {
    
    /** All searchable properties */
    private Collection<SearchableClassPropertyMapping> propertiesMapping;
    /** Owning domain class */
    private GrailsDomainClass domainClass;
    /** Searchable root? */
    private boolean root = true;
    private boolean all = true;

    /** Identifier properties used for indexing */
    private List<String> identityProperties;
    private String identitySeparator = ':';

    public SearchableClassMapping(GrailsDomainClass domainClass, Collection<SearchableClassPropertyMapping> propertiesMapping) {
        this.domainClass = domainClass;
        this.propertiesMapping = propertiesMapping;
        this.identityProperties = [domainClass.identifier.name];
    }

    public SearchableClassPropertyMapping getPropertyMapping(String propertyName) {
        for(SearchableClassPropertyMapping scpm : propertiesMapping) {
            if (scpm.getPropertyName().equals(propertyName)) {
                return scpm;
            }
        }
        return null;
    }

    public Boolean isRoot() {
        return root;
    }

    public void setRoot(Boolean root) {
        this.root = root != null && root;
    }

    public Collection<SearchableClassPropertyMapping> getPropertiesMapping() {
        return propertiesMapping;
    }

    public GrailsDomainClass getDomainClass() {
        return domainClass;
    }

    public List<String> getIdentityProperties() {
        return identityProperties;
    }

    public void setIdentityProperties(List<String> propertyNames) {
        this.identityProperties = propertyNames;
    }

    public String getIdentitySeparator() {
        return identitySeparator;
    }

    public void setIdentitySeparator(String separator) {
        this.identitySeparator = separator;
    }

    /**
     * Validate searchable class mapping.
     * @param contextHolder context holding all known searchable mappings.
     */
    public void validate(ElasticSearchContextHolder contextHolder) {
        for(SearchableClassPropertyMapping scpm : propertiesMapping) {
            scpm.validate(contextHolder);
        }
    }


    /**
     * @return ElasticSearch index name
     */
    public String getIndexName() {
        String name = domainClass.grailsApplication.config.elasticSearch.index.name ?: domainClass.packageName
        if (name == null || name.length() == 0) {
            // index name must be lowercase (org.elasticsearch.indices.InvalidIndexNameException)
            name = domainClass.getPropertyName();
        }
        return name.toLowerCase();
    }

    /**
     * @return type name for ES mapping.
     */
    public String getElasticTypeName() {
        // dot in ES type cause some issue on elastic search
        return domainClass.getFullName().toLowerCase().replace('.', '_');
    }

    public boolean isAll() {
        return all;
    }
}

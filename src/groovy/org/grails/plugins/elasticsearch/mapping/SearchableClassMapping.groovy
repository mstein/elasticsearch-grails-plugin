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

package org.grails.plugins.elasticsearch.mapping

import grails.util.GrailsNameUtils
import org.codehaus.groovy.grails.commons.GrailsDomainClass
import org.grails.plugins.elasticsearch.ElasticSearchContextHolder

class SearchableClassMapping {

    /** All searchable properties */
    private Collection<SearchableClassPropertyMapping> propertiesMapping
    /** Owning domain class */
    private GrailsDomainClass domainClass
    /** Searchable root? */
    private boolean root = true
    protected all = true

    public static final READ_SUFFIX = "_read"
    public static final WRITE_SUFFIX = "_write"

    String indexName

    SearchableClassMapping(GrailsDomainClass domainClass, Collection<SearchableClassPropertyMapping> propertiesMapping) {
        this.domainClass = domainClass
        this.propertiesMapping = propertiesMapping
        this.indexName = calculateIndexName()
    }

    SearchableClassPropertyMapping getPropertyMapping(String propertyName) {
        for (SearchableClassPropertyMapping scpm : propertiesMapping) {
            if (scpm.getPropertyName().equals(propertyName)) {
                return scpm
            }
        }
        return null
    }

    Boolean isRoot() {
        return root
    }

    void setRoot(Boolean root) {
        this.root = root != null && root
    }

    void setAll(all) {
        if (all != null)
            this.all = all
    }

    Collection<SearchableClassPropertyMapping> getPropertiesMapping() {
        return propertiesMapping
    }

    GrailsDomainClass getDomainClass() {
        return domainClass
    }

    /**
     * Validate searchable class mapping.
     * @param contextHolder context holding all known searchable mappings.
     */
    void validate(ElasticSearchContextHolder contextHolder) {
        for (SearchableClassPropertyMapping scpm : propertiesMapping) {
            scpm.validate(contextHolder)
        }
    }

    String calculateIndexName() {
        String name = domainClass.grailsApplication?.config?.elasticSearch?.index?.name ?: domainClass.packageName
        if (name == null || name.length() == 0) {
            // index name must be lowercase (org.elasticsearch.indices.InvalidIndexNameException)
            name = domainClass.getPropertyName()
        }
        return name.toLowerCase()
    }

    String getIndexingIndex() {
        return indexName + WRITE_SUFFIX
    }

    String getQueryingIndex() {
        return indexName + READ_SUFFIX
    }

    /**
     * @return type name for ES mapping.
     */
    String getElasticTypeName() {
        GrailsNameUtils.getPropertyName(domainClass.clazz)
    }

    boolean isAll() {
        if (all instanceof Boolean) {
            return all
        } else if (all instanceof Map) {
            return all.enabled instanceof Boolean ? all.enabled : true
        }
        return true
    }

    @Override
    public String toString() {
        return "${getClass().name}(domainClass:$domainClass, propertiesMapping:$propertiesMapping, indexName:$indexName, isAll:${isAll()})"
    }

}

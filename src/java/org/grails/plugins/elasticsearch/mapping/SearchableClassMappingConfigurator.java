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

import grails.converters.JSON;
import groovy.util.ConfigObject;
import org.apache.log4j.Logger;
import org.codehaus.groovy.grails.commons.DomainClassArtefactHandler;
import org.codehaus.groovy.grails.commons.GrailsApplication;
import org.codehaus.groovy.grails.commons.GrailsClass;
import org.codehaus.groovy.grails.commons.GrailsDomainClass;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.Requests;
import org.elasticsearch.indices.IndexAlreadyExistsException;
import org.elasticsearch.transport.RemoteTransportException;
import org.grails.plugins.elasticsearch.ElasticSearchContextHolder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Build searchable mappings, configure ElasticSearch indexes,
 * build and install ElasticSearch mappings.
 */
public class SearchableClassMappingConfigurator {

    private static final Logger LOG = Logger.getLogger(SearchableClassMappingConfigurator.class);

    private ElasticSearchContextHolder elasticSearchContext;
    private GrailsApplication grailsApplication;
    private Client elasticSearchClient;
    private ConfigObject config;

    /**
     * Init method.
     */
    public void configureAndInstallMappings() {
        Collection<SearchableClassMapping> mappings = buildMappings();
        installMappings(mappings);
    }

    /**
     * Resolve the ElasticSearch mapping from the static "searchable" property (closure or boolean) in domain classes
     * @param mappings searchable class mappings to be install.
     */
    public void installMappings(Collection<SearchableClassMapping> mappings) {
        for(SearchableClassMapping scm : mappings) {

            if (scm.isRoot()) {
                Map elasticMapping = ElasticSearchMappingFactory.getElasticMapping(scm);

                LOG.debug(elasticMapping.toString());

                // todo wait for success, maybe retry.
                try {
                    elasticSearchClient.admin().indices().prepareCreate(scm.getIndexName())
                            .execute().actionGet();
                    // If the index already exists, ignore the exception
                } catch (IndexAlreadyExistsException iaee) {
                    LOG.debug(iaee.getMessage());
                } catch (RemoteTransportException rte) {
                    LOG.debug(rte.getMessage());
                }

                PutMappingRequest putMapping = Requests.putMappingRequest(scm.getIndexName());
                putMapping.source(elasticMapping);
                elasticSearchClient.admin().indices().putMapping(putMapping).actionGet();
            }

        }
    }

    private Collection<SearchableClassMapping> buildMappings() {
        List<SearchableClassMapping> mappings = new ArrayList<SearchableClassMapping>();
        for(GrailsClass clazz : grailsApplication.getArtefacts(DomainClassArtefactHandler.TYPE)) {
            GrailsDomainClass domainClass = (GrailsDomainClass) clazz;
            LOG.debug("Custom mapping for searchable detected in [" + domainClass.getPropertyName() + "] class, resolving the closure...");
            ClosureSearchableDomainClassMapper closureMapper = new ClosureSearchableDomainClassMapper(domainClass, config);
            SearchableClassMapping searchableClassMapping = closureMapper.buildClassMapping();
            if (searchableClassMapping != null) {
                elasticSearchContext.addMappingContext(searchableClassMapping);
                mappings.add(searchableClassMapping);
            }
        }

        // Validate all mappings to make sure any cross-references are fine.
        for(SearchableClassMapping scm : mappings) {
            scm.validate(elasticSearchContext);
        }

        return mappings;
    }

    public void setElasticSearchContext(ElasticSearchContextHolder elasticSearchContext) {
        this.elasticSearchContext = elasticSearchContext;
    }

    public void setGrailsApplication(GrailsApplication grailsApplication) {
        this.grailsApplication = grailsApplication;
    }

    public void setElasticSearchClient(Client elasticSearchClient) {
        this.elasticSearchClient = elasticSearchClient;
    }

    public void setConfig(ConfigObject config) {
        this.config = config;
    }
}

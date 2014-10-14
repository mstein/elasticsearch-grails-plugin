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

import org.codehaus.groovy.grails.commons.DomainClassArtefactHandler
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.commons.GrailsClass
import org.codehaus.groovy.grails.commons.GrailsDomainClass
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingRequest
import org.elasticsearch.client.Client
import org.elasticsearch.indices.IndexAlreadyExistsException
import org.elasticsearch.transport.RemoteTransportException
import org.grails.plugins.elasticsearch.ElasticSearchContextHolder
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Build searchable mappings, configure ElasticSearch indexes,
 * build and install ElasticSearch mappings.
 */
class SearchableClassMappingConfigurator {

    private static final Logger LOG = LoggerFactory.getLogger(this)

    private ElasticSearchContextHolder elasticSearchContext
    private GrailsApplication grailsApplication
    private Client elasticSearchClient
    private ConfigObject config

    /**
     * Init method.
     */
    public void configureAndInstallMappings() {
        Collection<SearchableClassMapping> mappings = mappings()
        installMappings(mappings)
    }

    public Collection<SearchableClassMapping> mappings() {
        List<SearchableClassMapping> mappings = []
        for (GrailsClass clazz : grailsApplication.getArtefacts(DomainClassArtefactHandler.TYPE)) {
            GrailsDomainClass domainClass = (GrailsDomainClass) clazz
            SearchableDomainClassMapper mapper = new SearchableDomainClassMapper(grailsApplication, domainClass, config)
            SearchableClassMapping searchableClassMapping = mapper.buildClassMapping()
            if (searchableClassMapping != null) {
                elasticSearchContext.addMappingContext(searchableClassMapping)
                mappings.add(searchableClassMapping)
            }
        }

        // Inject cross-referenced component mappings.
        for (SearchableClassMapping scm : mappings) {
            for (SearchableClassPropertyMapping scpm : scm.getPropertiesMapping()) {
                if (scpm.isComponent()) {
                    Class<?> componentType = scpm.getGrailsProperty().getReferencedPropertyType()
                    scpm.setComponentPropertyMapping(elasticSearchContext.getMappingContextByType(componentType))
                }
            }
        }

        // Validate all mappings to make sure any cross-references are fine.
        for (SearchableClassMapping scm : mappings) {
            scm.validate(elasticSearchContext)
        }

        return mappings
    }

    /**
     * Resolve the ElasticSearch mapping from the static "searchable" property (closure or boolean) in domain classes
     * @param mappings searchable class mappings to be install.
     */
    public void installMappings(Collection<SearchableClassMapping> mappings) {
        Set<String> installedIndices = []
        Map<String, Object> settings = new HashMap<String, Object>()
//        settings.put("number_of_shards", 5)        // must have 5 shards to be Green.
//        settings.put("number_of_replicas", 2)
        settings.put("number_of_replicas", 0)
        // Look for default index settings.
        Map esConfig = grailsApplication.config.getProperty("elasticSearch")
        if (esConfig != null) {
            Map<String, Object> indexDefaults = esConfig.get("index")
            LOG.debug("Retrieved index settings")
            if (indexDefaults != null) {
                for (Map.Entry<String, Object> entry : indexDefaults.entrySet()) {
                    settings.put("index." + entry.getKey(), entry.getValue())
                }
            }
        }
        LOG.debug("Installing mappings...")
        for (SearchableClassMapping scm : mappings) {
            if (scm.isRoot()) {
                Map elasticMapping = ElasticSearchMappingFactory.getElasticMapping(scm)

                // todo wait for success, maybe retry.
                // If the index does not exist, create it
                if (!installedIndices.contains(scm.getIndexName())) {
                    LOG.debug("Index ${scm.indexName} does not exists, initiating creation...")
                    try {
                        // Could be blocked on index level, thus wait.
                        try {
                            LOG.debug("Waiting at least yellow status on ${scm.indexName}")
                            elasticSearchClient.admin().cluster().prepareHealth(scm.getIndexName())
                                    .setWaitForYellowStatus()
                                    .execute().actionGet()
                        } catch (Exception e) {
                            // ignore any exceptions due to non-existing index.
                            LOG.debug('Index health', e)
                        }
                        elasticSearchClient.admin().indices().prepareCreate(scm.getIndexName())
                                .setSettings(settings)
                                .execute().actionGet()
                        installedIndices.add(scm.getIndexName())
                        LOG.debug(elasticMapping.toString())

                        // If the index already exists, ignore the exception
                    } catch (IndexAlreadyExistsException iaee) {
                        LOG.debug("Index ${scm.indexName} already exists, skip index creation.")
                    } catch (RemoteTransportException rte) {
                        LOG.debug(rte.getMessage())
                    }
                }

                // Install mapping
                // todo when conflict is detected, delete old mapping (this will delete all indexes as well, so should warn user)
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[" + scm.getElasticTypeName() + "] => " + elasticMapping)
                }
                elasticSearchClient.admin().indices().putMapping(
                        new PutMappingRequest(scm.getIndexName())
                                .type(scm.getElasticTypeName())
                                .source(elasticMapping)
                ).actionGet()
            }

        }

        ClusterHealthResponse response = elasticSearchClient.admin().cluster().health(
                new ClusterHealthRequest([] as String[]).waitForYellowStatus()).actionGet()
        LOG.debug("Cluster status: ${response.status}")
    }

    void setElasticSearchContext(ElasticSearchContextHolder elasticSearchContext) {
        this.elasticSearchContext = elasticSearchContext
    }

    void setGrailsApplication(GrailsApplication grailsApplication) {
        this.grailsApplication = grailsApplication
    }

    void setElasticSearchClient(Client elasticSearchClient) {
        this.elasticSearchClient = elasticSearchClient
    }

    void setConfig(ConfigObject config) {
        this.config = config
    }
}

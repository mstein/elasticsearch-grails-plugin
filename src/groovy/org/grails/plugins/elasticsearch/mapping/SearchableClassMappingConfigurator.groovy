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
import org.elasticsearch.client.Client
import org.elasticsearch.index.mapper.MergeMappingException
import org.elasticsearch.transport.RemoteTransportException
import org.grails.plugins.elasticsearch.ElasticSearchContextHolder
import org.grails.plugins.elasticsearch.exception.MappingException
import org.grails.plugins.elasticsearch.util.ElasticSearchShortcuts
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import static org.grails.plugins.elasticsearch.mapping.MappingMigrationStrategy.*

/**
 * Build searchable mappings, configure ElasticSearch indexes,
 * build and install ElasticSearch mappings.
 */
class SearchableClassMappingConfigurator {

    private static final Logger LOG = LoggerFactory.getLogger(this)

    private ElasticSearchContextHolder elasticSearchContext
    private GrailsApplication grailsApplication
    private Client elasticSearchClient
    private ElasticSearchShortcuts es
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
        MappingMigrationStrategy migrationStrategy = esConfig?.migration?.strategy ? MappingMigrationStrategy.valueOf(esConfig.migration.strategy) : none
        def conflictingMappings = []
        Map elasticMappings = [:]
        for (SearchableClassMapping scm : mappings) {
            if (scm.isRoot()) {
                elasticMappings << [scm: ElasticSearchMappingFactory.getElasticMapping(scm)]
            }
        }
        for (SearchableClassMapping scm : mappings) {
            if (scm.isRoot()) {
                Map elasticMapping = elasticMappings[scm]

                // todo wait for success, maybe retry.
                // If the index was not created, create it
                if (!installedIndices.contains(scm.indexName)) {
                    try {
                        safeCreateIndex(migrationStrategy, scm.indexName, settings)
                        installedIndices.add(scm.indexName)
                    } catch (RemoteTransportException rte) {
                        LOG.debug(rte.getMessage())
                    }
                }

                // Install mapping
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Installing mapping [" + scm.elasticTypeName + "] => " + elasticMapping)
                }
                try {
                    es.createMapping scm.indexName, scm.elasticTypeName, elasticMapping
                } catch (MergeMappingException e) {
                    LOG.warn("Could not install mapping ${scm.indexName}/${scm.elasticTypeName} due to ${e.message}, migrations needed")
                    conflictingMappings << [scm: scm, exception: e, elasticMapping: elasticMapping]
                }
            }
        }

        if(conflictingMappings) {
            LOG.info("Applying migrations ...")
            switch(migrationStrategy) {
                case delete:
                    conflictingMappings.each {
                        es.deleteMapping it.scm.indexName, it.scm.elasticTypeName
                        es.createMapping it.scm.indexName, it.scm.elasticTypeName, it.elasticMapping
                        //TODO mark as recreated for indexing on Bootstrap!
                    }
                    break;
                case alias:
                    def migratedIndices = []
                    conflictingMappings.each {
                        if (!migratedIndices.contains(it.scm.indexName)) {
                            boolean isAlias = es.aliasExists(it.scm.indexName)
                            if(isAlias || esConfig.migration.aliasReplacesIndex ) {
                                int nextVersion = 0
                                if (isAlias) {
                                    nextVersion = es.getNextVersion(it.scm.indexName)
                                } else {
                                    es.deleteIndex(it.scm.indexName)
                                }
                                es.createIndex it.scm.indexName, nextVersion, settings
                                es.pointAliasTo it.scm.indexName, it.scm.indexName, nextVersion
                                migratedIndices << it.scm.indexName
                                //TODO mark as recreated for indexing on Bootstrap!
                            } else {
                                throw new MappingException("Could not create alias ${it.scm.indexName} due to error installing mapping ${it.scm.elasticTypeName}, index with the same name already exists.", it.exception)
                            }
                        }
                    }
                    //Recreate the mappings for all the indexes that were changed
                    migratedIndices.each { migratedIndex ->
                        elasticMappings.each { scm, elasticMapping ->
                            if (scm.indexName == migratedIndex) {
                                es.createMapping(migratedIndex, scm.elasticTypeName, elasticMapping)
                            }
                        }
                    }
                    break;
                case none:
                    LOG.error("Could not install mappings : ${conflictingMappings} and no migration strategy selected.")
                    throw new MappingException()
            }
        }

        ClusterHealthResponse response = elasticSearchClient.admin().cluster().health(
                new ClusterHealthRequest([] as String[]).waitForYellowStatus()).actionGet()
        LOG.debug("Cluster status: ${response.status}")
    }


    /**
     * Creates the Elasticsearch index once unblocked
     * @param indexName
     * @returns true if it created a new index, false if it already existed
     * @throws RemoteTransportException if some other error occured
     */
    private boolean safeCreateIndex(MappingMigrationStrategy strategy, String indexName, Map settings) throws RemoteTransportException {
        LOG.debug("Index ${indexName} does not exists, initiating creation...")
        // Could be blocked on index level, thus wait.
        try {
            LOG.debug("Waiting at least yellow status on ${indexName}")
            elasticSearchClient.admin().cluster().prepareHealth(indexName)
                    .setWaitForYellowStatus()
                    .execute().actionGet()
        } catch (Exception e) {
            // ignore any exceptions due to non-existing index.
            LOG.debug('Index health', e)
        }
        if(!es.indexExists(indexName)) {
            if (strategy == alias) {
                es.createIndex indexName, 0, settings
                es.pointAliasTo indexName, indexName, 0
            } else {
                es.createIndex indexName, settings
            }
        }
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

    void setEs(ElasticSearchShortcuts es) {
        this.es = es
    }

    void setConfig(ConfigObject config) {
        this.config = config
    }
}

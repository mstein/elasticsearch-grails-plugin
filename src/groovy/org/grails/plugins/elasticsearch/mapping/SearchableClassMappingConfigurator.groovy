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
import org.elasticsearch.index.mapper.MergeMappingException
import org.elasticsearch.transport.RemoteTransportException
import org.grails.plugins.elasticsearch.ElasticSearchAdminService
import org.grails.plugins.elasticsearch.ElasticSearchContextHolder
import org.grails.plugins.elasticsearch.exception.MappingException
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
    private ElasticSearchAdminService es
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
                elasticMappings << [(scm) : ElasticSearchMappingFactory.getElasticMapping(scm)]
            }
        }
        LOG.debug "elasticMappings are ${elasticMappings.keySet()}"
        for (SearchableClassMapping scm : mappings) {
            if (scm.isRoot()) {

                assert scm.indexingIndex == scm.queryingIndex

                Map elasticMapping = elasticMappings[scm]

                // todo wait for success, maybe retry.
                // If the index was not created, create it
                if (!installedIndices.contains(scm.queryingIndex)) {
                    try {
                        safeCreateIndex(migrationStrategy, scm.queryingIndex, settings)
                        installedIndices.add(scm.queryingIndex)
                    } catch (RemoteTransportException rte) {
                        LOG.debug(rte.getMessage())
                    }
                }

                // Install mapping
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Installing mapping [" + scm.elasticTypeName + "] => " + elasticMapping)
                }
                try {
                    es.createMapping scm.queryingIndex, scm.elasticTypeName, elasticMapping
                } catch (MergeMappingException e) {
                    LOG.warn("Could not install mapping ${scm.queryingIndex}/${scm.elasticTypeName} due to ${e.message}, migrations needed")
                    conflictingMappings << [scm: scm, exception: e, elasticMapping: elasticMapping]
                }
            }
        }

        if(conflictingMappings) {
            LOG.info("Applying migrations ...")
            switch(migrationStrategy) {
                case delete:
                    conflictingMappings.each {
                        SearchableClassMapping scm = it.scm
                        es.deleteMapping scm.queryingIndex, scm.elasticTypeName
                        es.createMapping scm.queryingIndex, scm.elasticTypeName, it.elasticMapping
                        elasticSearchContext.deleted << scm.domainClass.clazz
                    }
                    break;
                case alias:
                    def migratedIndices = []
                    conflictingMappings.each {
                        SearchableClassMapping scm = it.scm
                        if (!migratedIndices.contains(scm.queryingIndex)) {
                            boolean conflictIsOnAlias = es.aliasExists(scm.queryingIndex)
                            if(conflictIsOnAlias || esConfig.migration.aliasReplacesIndex ) {
                                int nextVersion = 0
                                if (conflictIsOnAlias) {
                                    nextVersion = es.getNextVersion(scm.queryingIndex)
                                } else {
                                    es.deleteIndex(scm.queryingIndex)
                                }
                                es.createIndex scm.queryingIndex, nextVersion, settings
                                es.waitForIndex scm.queryingIndex, nextVersion //Ensure it exists so later on mappings are created on the right version

                                if(!esConfig.bulkIndexOnStartup) { //Otherwise, it will be done post content creation
                                    if (conflictIsOnAlias && !esConfig.migration.disableAliasChange) {
                                        es.pointAliasTo scm.queryingIndex, scm.queryingIndex, nextVersion
                                    }
                                }
                                migratedIndices << scm.queryingIndex
                            } else {
                                throw new MappingException("Could not create alias ${scm.queryingIndex} to solve error installing mapping ${scm.elasticTypeName}, index with the same name already exists.", it.exception)
                            }
                        }
                    }
                    //Recreate the mappings for all the indexes that were changed
                    elasticMappings.each { SearchableClassMapping scm, elasticMapping ->
                        if (migratedIndices.contains(scm.queryingIndex)) {
                            elasticSearchContext.deleted << scm.domainClass.clazz //Mark it for potential content index on Bootstrap
                            if (scm.isRoot()) {
                                int newVersion = es.getLatestVersion(scm.queryingIndex)
                                String indexName = es.versionIndex(scm.queryingIndex, newVersion)
                                es.createMapping(indexName, scm.elasticTypeName, elasticMapping)
                                if(esConfig.bulkIndexOnStartup) { //Content needs to be indexed on the new index
                                    scm.indexingIndex = indexName
                                }
                            }
                        }
                    }
                    break;
                case none:
                    LOG.error("Could not install mappings : ${conflictingMappings} and no migration strategy selected.")
                    throw new MappingException()
            }
        }

        es.waitForClusterYellowStatus()
    }


    /**
     * Creates the Elasticsearch index once unblocked
     * @param indexName
     * @returns true if it created a new index, false if it already existed
     * @throws RemoteTransportException if some other error occured
     */
    private boolean safeCreateIndex(MappingMigrationStrategy strategy, String indexName, Map settings) throws RemoteTransportException {
        // Could be blocked on index level, thus wait.
        es.waitForIndex(indexName)
        if(!es.indexExists(indexName)) {
            LOG.debug("Index ${indexName} does not exists, initiating creation...")
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

    void setEs(ElasticSearchAdminService es) {
        this.es = es
    }

    void setConfig(ConfigObject config) {
        this.config = config
    }
}

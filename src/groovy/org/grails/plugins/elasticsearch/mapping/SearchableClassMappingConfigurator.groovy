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
    private MappingMigrationManager mmm
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
    public void installMappings(Collection<SearchableClassMapping> mappings){

        Map esConfig = grailsApplication.config.getProperty("elasticSearch")
        Map<String, Object> indexSettings = buildIndexSettings(esConfig)

        LOG.debug("Index settings are " + indexSettings)
        
        LOG.debug("Installing mappings...")
        Map<SearchableClassMapping, Map> elasticMappings = buildElasticMappings(mappings)
        LOG.debug "elasticMappings are ${elasticMappings.keySet()}"

        MappingMigrationStrategy migrationStrategy = esConfig?.migration?.strategy ? MappingMigrationStrategy.valueOf(esConfig.migration.strategy) : none
        Set<String> installedIndices = []
        def mappingConflicts = []
        for (SearchableClassMapping scm : mappings) {
            if (scm.isRoot()) {

                Map elasticMapping = elasticMappings[scm]

                // todo wait for success, maybe retry.
                // If the index was not created, create it
                if (!installedIndices.contains(scm.indexName)) {
                    try {
                        createIndexWithReadAndWrite(migrationStrategy, scm, indexSettings)
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
                    mappingConflicts << new MappingConflict(scm: scm, exception: e)
                }
            }
        }
        if(mappingConflicts) {
            LOG.info("Applying migrations ...")
            mmm.applyMigrations(migrationStrategy, elasticMappings, mappingConflicts, indexSettings)
        }

        es.waitForClusterYellowStatus()
    }


    /**
     * Creates the Elasticsearch index once unblocked and its read and write aliases
     * @param indexName
     * @returns true if it created a new index, false if it already existed
     * @throws RemoteTransportException if some other error occured
     */
    private boolean createIndexWithReadAndWrite(MappingMigrationStrategy strategy, SearchableClassMapping scm, Map indexSettings) throws RemoteTransportException {
        // Could be blocked on index level, thus wait.
        es.waitForIndex(scm.indexName)
        if(!es.indexExists(scm.indexName)) {
            LOG.debug("Index ${scm.indexName} does not exists, initiating creation...")
            if (strategy == alias) {
                def nextVersion = es.getNextVersion scm.indexName
                es.createIndex scm.indexName, nextVersion, indexSettings
                es.pointAliasTo scm.indexName, scm.indexName, nextVersion
            } else {
                es.createIndex scm.indexName, indexSettings
            }
        }
        //Create them only if they don't exist so it does not mess with other migrations
        if(!es.aliasExists(scm.queryingIndex)) {
            es.pointAliasTo(scm.queryingIndex, scm.indexName)
            es.pointAliasTo(scm.indexingIndex, scm.indexName)
        }
    }

    private Map<String, Object> buildIndexSettings(def esConfig) {
        Map<String, Object> indexSettings = new HashMap<String, Object>()
        indexSettings.put("number_of_replicas", numberOfReplicas())
        // Look for default index settings.
        if (esConfig != null) {
            Map<String, Object> indexDefaults = esConfig.get("index")
            LOG.debug("Retrieved index settings")
            if (indexDefaults != null) {
                for (Map.Entry<String, Object> entry : indexDefaults.entrySet()) {
                    indexSettings.put("index." + entry.getKey(), entry.getValue())
                }
            }
        }
        indexSettings
    }

    private Map<SearchableClassMapping, Map> buildElasticMappings(Collection<SearchableClassMapping> mappings) {
        Map<SearchableClassMapping, Map> elasticMappings = [:]
        for (SearchableClassMapping scm : mappings) {
            if (scm.isRoot()) {
                elasticMappings << [(scm) : ElasticSearchMappingFactory.getElasticMapping(scm)]
            }
        }
        elasticMappings
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

    void setMmm(MappingMigrationManager mmm) {
        this.mmm = mmm
    }
    void setConfig(ConfigObject config) {
        this.config = config
    }

    private int numberOfReplicas() {
        def defaultNumber = elasticSearchContext.config.index.numberOfReplicas
        if (!defaultNumber) {
            return 0
        }
        defaultNumber
    }
}

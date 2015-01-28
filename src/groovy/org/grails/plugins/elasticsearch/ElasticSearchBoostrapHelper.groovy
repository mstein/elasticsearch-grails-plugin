package org.grails.plugins.elasticsearch

import org.codehaus.groovy.grails.commons.GrailsApplication
import org.grails.plugins.elasticsearch.mapping.MappingMigrationStrategy
import org.grails.plugins.elasticsearch.mapping.SearchableClassMapping
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import static org.grails.plugins.elasticsearch.mapping.MappingMigrationStrategy.alias
import static org.grails.plugins.elasticsearch.mapping.MappingMigrationStrategy.none

/**
 * Created by @marcos-carceles on 13/01/15.
 * Created and exposed as a bean, because Bootstrap cannot be easily tested and invoked from IntegrationSpec
 */
class ElasticSearchBootStrapHelper {

    private static final Logger LOG = LoggerFactory.getLogger(this)

    private GrailsApplication grailsApplication
    private ElasticSearchService elasticSearchService
    private ElasticSearchAdminService elasticSearchAdminService
    private ElasticSearchContextHolder elasticSearchContextHolder

    void bulkIndexOnStartup() {
        def esConfig = grailsApplication.config.elasticSearch
        def bulkIndexOnStartup = esConfig?.bulkIndexOnStartup
        //Index Content
        if (bulkIndexOnStartup == "deleted") { //Index lost content due to migration
            LOG.debug "Performing bulk indexing of classes requiring index/mapping migration ${elasticSearchContextHolder.deletedOnMigration} on their new version."
            elasticSearchService.index(elasticSearchContextHolder.deletedOnMigration as Class[])
        } else if (bulkIndexOnStartup) { //Index all
            LOG.debug "Performing bulk indexing."
            elasticSearchService.index()
        }
        //Update index aliases where needed
        MappingMigrationStrategy migrationStrategy = esConfig?.migration?.strategy ? MappingMigrationStrategy.valueOf(esConfig.migration.strategy) : none
        if (migrationStrategy == alias) {
            elasticSearchContextHolder.deletedOnMigration.each { Class clazz ->
                SearchableClassMapping scm = elasticSearchContextHolder.getMappingContextByType(clazz)
                int latestVersion = elasticSearchAdminService.getLatestVersion(scm.indexName)
                if(!esConfig.migration.disableAliasChange) {
                    elasticSearchAdminService.pointAliasTo scm.queryingIndex, scm.indexName, latestVersion
                }
                elasticSearchAdminService.pointAliasTo scm.indexingIndex, scm.indexName, latestVersion
            }
        }
    }

    void setGrailsApplication(GrailsApplication grailsApplication) {
        this.grailsApplication = grailsApplication
    }

    void setElasticSearchService(ElasticSearchService elasticSearchService) {
        this.elasticSearchService = elasticSearchService
    }

    void setElasticSearchAdminService(ElasticSearchAdminService elasticSearchAdminService) {
        this.elasticSearchAdminService = elasticSearchAdminService
    }

    void setElasticSearchContextHolder(ElasticSearchContextHolder elasticSearchContextHolder) {
        this.elasticSearchContextHolder = elasticSearchContextHolder
    }

}

package org.grails.plugins.elasticsearch.util

import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest
import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder
import org.elasticsearch.action.admin.indices.exists.types.TypesExistsRequest
import org.elasticsearch.action.admin.indices.mapping.delete.DeleteMappingRequest
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingRequest
import org.elasticsearch.client.Client
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Created by @marcos-carceles on 07/01/15.
 * This bean was generated as a set of abbreviations for common admin tasks when we want to run them synchronously
 */
class ElasticSearchShortcuts {

    private static final Logger LOG = LoggerFactory.getLogger(this)

    private static final MAX_RETRIES = 10
    private static final SLEEP_INTERVAL = 100

    Client elasticSearchClient

    void deleteMapping(String index, String type) {
        LOG.info("Deleting Elasticsearch mapping for ${index} and type ${type} ...")
        elasticSearchClient.admin().indices().deleteMapping(
                new DeleteMappingRequest(index).
                        types(type)
        ).actionGet()
    }

    void createMapping(String index, String type, Map elasticMapping) {
        LOG.info("Creating Elasticsearch mapping for ${index} and type ${type} ...")
        elasticSearchClient.admin().indices().putMapping(
                new PutMappingRequest(index)
                        .type(type)
                        .source(elasticMapping)
        ).actionGet()
    }

    boolean mappingExists(String index, String type) {
        elasticSearchClient.admin().indices().typesExists(new TypesExistsRequest([index] as String[], type)).actionGet().exists
    }

    void deleteIndex(String index) {
        LOG.info("Deleting  Elasticsearch index ${index} ...")
        elasticSearchClient.admin().indices().prepareDelete(index).execute().actionGet()
    }

    void createIndex(String index, Map settings=null) {
        LOG.debug "Creating index ${index} ..."
        CreateIndexRequestBuilder builder = elasticSearchClient.admin().indices().prepareCreate(index)
        if(settings) {
            builder.setSettings(settings)
        }
        builder.execute().actionGet()
    }

    void createIndex(String index, Integer version, Map settings=null) {
        index = versionIndex(index, version)
        createIndex(index, settings)
    }

    boolean indexExists(String index, Integer version = null) {
        index = versionIndex(index, version)
        elasticSearchClient.admin().indices().prepareExists(index).execute().actionGet().exists
    }

    void waitForIndex(String index, int version) {
        int retries = MAX_RETRIES
        while(getLatestVersion(index) < version && retries--) {
            LOG.debug("Index ${versionIndex(index, version)} not found, sleeping for ${SLEEP_INTERVAL}...")
            Thread.sleep(SLEEP_INTERVAL)
        }
    }

    String indexPointedBy(String alias) {
        def index = elasticSearchClient.admin().indices().getAliases(new GetAliasesRequest().aliases([alias] as String[])).actionGet().getAliases()?.find { it.value.element.alias() == alias }?.key
        if(!index) {
            LOG.debug("Alias ${alias} does not exist")
        }
        return index
    }

    void pointAliasTo(String alias, String index, Integer version = null) {
        index = versionIndex(index, version)
        LOG.debug "Creating alias ${alias}, pointing to index ${index} ..."
        String oldIndex = indexPointedBy(alias)
        //Create atomic operation
        def aliasRequest = elasticSearchClient.admin().indices().prepareAliases()
        if (oldIndex && oldIndex != index) {
            LOG.debug "Index used to point to ${oldIndex}, removing ..."
            aliasRequest.removeAlias(oldIndex,alias)
        }
        aliasRequest.   addAlias(index,alias)
        aliasRequest.execute().actionGet()
    }

    boolean aliasExists(String alias) {
        elasticSearchClient.admin().indices().prepareAliasesExist(alias).execute().actionGet().exists
    }

    String versionIndex(String index, Integer version = null) {
        version == null ? index : index + "_v${version}"
    }

    int getNextVersion(String index) {
        Set indices = elasticSearchClient.admin().indices().prepareStats().execute().actionGet().indices.keySet()
        indices.count {
            it =~ /^${index}_v\d+$/
        }
    }

    int getLatestVersion(String index) {
        getNextVersion(index) - 1
    }

    def waitForIndex(index) {
        try {
            LOG.debug("Waiting at least yellow status on ${index}")
            elasticSearchClient.admin().cluster().prepareHealth(index)
                    .setWaitForYellowStatus()
                    .execute().actionGet()
        } catch (Exception e) {
            // ignore any exceptions due to non-existing index.
            LOG.debug('Index health', e)
        }
    }
}

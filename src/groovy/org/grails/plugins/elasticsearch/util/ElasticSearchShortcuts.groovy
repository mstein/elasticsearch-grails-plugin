package org.grails.plugins.elasticsearch.util

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

    void createIndex(String index, Integer version = null) {
        index = versionIndex(index, version)
        LOG.debug "Creating index ${index} ..."
        elasticSearchClient.admin().indices().prepareCreate(index).execute().actionGet()
    }

    boolean indexExists(String index) {
        elasticSearchClient.admin().indices().prepareExists(index).execute().actionGet().exists
    }

    boolean aliasExists(String alias) {
        elasticSearchClient.admin().indices().prepareAliasesExist(alias).execute().actionGet().exists
    }

    private String versionIndex(String index, Integer version = null) {
        version == null ? index : index + "_v${version}"
    }
}

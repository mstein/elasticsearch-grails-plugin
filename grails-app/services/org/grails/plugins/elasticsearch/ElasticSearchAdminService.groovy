package org.grails.plugins.elasticsearch

import org.apache.log4j.Logger
import org.elasticsearch.client.Client
import org.elasticsearch.action.support.broadcast.BroadcastOperationResponse
import org.elasticsearch.client.Requests

class ElasticSearchAdminService {
    static transactional = false
    static LOG = Logger.getLogger(ElasticSearchAdminService.class)

    def elasticSearchHelper
    def elasticSearchContextHolder

    /**
     * Explicitly refresh ALL index, making all operations performed since the last refresh available for search
     * TODO : also flush all pending requests and wait for the response from ES ?
     */
    public void refresh() {
        elasticSearchHelper.withElasticSearch {Client client ->
            BroadcastOperationResponse response = client.admin().indices().refresh(Requests.refreshRequest()).actionGet()
            if (response.failedShards() > 0) {
                LOG.info "Refresh failure"
            } else {
                LOG.info "Refreshed all indices"
            }
        }
    }

    /**
     * Delete one or more index and all its data.
     * @param indices The indices to delete. If null, will delete ALL indices.
     */
    public void deleteIndex(Collection<String> indices = null) {
        elasticSearchHelper.withElasticSearch { Client client ->
            if (!indices) {
                client.admin().indices().delete(Requests.deleteIndexRequest("_all")).actionGet()
                LOG.info "Deleted all indices"
            } else {
                indices.each {
                    client.admin().indices().delete(Requests.deleteIndexRequest(it)).actionGet()
                }
                LOG.info "Deleted indices $indices"
            }
        }
    }

    /**
     * Delete one or more index and all its data.
     * @param indices The indices to delete. If null, will delete ALL indices.
     */
    public void deleteIndex(String... indices) {
        deleteIndex(indices as Collection<String>)
    }

    /**
     * Delete one or more index and all its data.
     * @param indices The indices to delete in the form of searchable class(es). If null, will delete ALL indices.
     */
    public void deleteIndex(Class... searchableClass) {
        def toDelete = []

        // Retrieve indices to delete
        searchableClass.each {
            def scm = elasticSearchContextHolder.getMappingContextByType(it)
            toDelete << scm.indexName
        }

        deleteIndex(toDelete.unique())
    }

}

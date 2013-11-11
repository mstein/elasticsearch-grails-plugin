package org.grails.plugins.elasticsearch

import org.elasticsearch.action.support.broadcast.BroadcastOperationResponse
import org.elasticsearch.client.Client
import org.elasticsearch.client.Requests
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ElasticSearchAdminService {

    static transactional = false

    static final Logger LOG = LoggerFactory.getLogger(this)

    def elasticSearchHelper
    def elasticSearchContextHolder
    def indexRequestQueue

    /**
     * Explicitly refresh one or more index, making all operations performed since the last refresh available for search
     * This method will also flush all pending request in the indexRequestQueue and will wait for their completion.
     * @param indices The indices to refresh. If null, will refresh ALL indices.
     */
    void refresh(Collection<String> indices = null) {
        // Flush any pending operation from the index queue
        indexRequestQueue.executeRequests()
        // Wait till all the current operations are done
        indexRequestQueue.waitComplete()

        // Refresh ES
        elasticSearchHelper.withElasticSearch { Client client ->
            BroadcastOperationResponse response
            if (!indices) {
                response = client.admin().indices().refresh(Requests.refreshRequest()).actionGet()
            } else {
                response = client.admin().indices().refresh(Requests.refreshRequest(indices as String[])).actionGet()
            }

            if (response.getFailedShards() > 0) {
                LOG.info "Refresh failure"
            } else {
                LOG.info "Refreshed ${ indices ?: 'all' } indices"
            }
        }
    }

    /**
     * Explicitly refresh one or more index, making all operations performed since the last refresh available for search
     * This method will also flush all pending request in the indexRequestQueue and will wait for their completion.
     * @param indices The indices to refresh. If null, will refresh ALL indices.
     */
    void refresh(String... indices) {
        refresh(indices as Collection<String>)
    }

    /**
     * Explicitly refresh ALL index, making all operations performed since the last refresh available for search
     * This method will also flush all pending request in the indexRequestQueue and will wait for their completion.
     * @param searchableClasses The indices represented by the specified searchable classes to refresh. If null, will refresh ALL indices.
     */
    void refresh(Class... searchableClasses) {
        def toRefresh = []

        // Retrieve indices to refresh
        searchableClasses.each {
            def scm = elasticSearchContextHolder.getMappingContextByType(it)
            if (scm) {
                toRefresh << scm.indexName
            }
        }

        refresh(toRefresh.unique())
    }

    /**
     * Delete one or more index and all its data.
     * @param indices The indices to delete. If null, will delete ALL indices.
     */
    void deleteIndex(Collection<String> indices = null) {
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
    void deleteIndex(String... indices) {
        deleteIndex(indices as Collection<String>)
    }

    /**
     * Delete one or more index and all its data.
     * @param indices The indices to delete in the form of searchable class(es).
     */
    void deleteIndex(Class... searchableClasses) {
        def toDelete = []

        // Retrieve indices to delete
        searchableClasses.each {
            def scm = elasticSearchContextHolder.getMappingContextByType(it)
            if (scm) {
                toDelete << scm.indexName
            }
        }
        // We do not trigger the deleteIndex with an empty list as it would delete ALL indices.
        // If toDelete is empty, it might be because of a misuse of a Class the user thought to be a searchable class
        if (!toDelete.isEmpty()) {
            deleteIndex(toDelete.unique())
        }
    }
}

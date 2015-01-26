package org.grails.plugins.elasticsearch

import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest
import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder
import org.elasticsearch.action.admin.indices.exists.types.TypesExistsRequest
import org.elasticsearch.action.admin.indices.mapping.delete.DeleteMappingRequest
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingRequest
import org.elasticsearch.action.support.broadcast.BroadcastOperationResponse
import org.elasticsearch.client.Client
import org.elasticsearch.client.Requests
import org.grails.plugins.elasticsearch.mapping.SearchableClassMapping
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.regex.Matcher

class ElasticSearchAdminService {

    static transactional = false

    static final Logger LOG = LoggerFactory.getLogger(this)

    def elasticSearchHelper
    def elasticSearchContextHolder
    def indexRequestQueue

    private static final WAIT_FOR_INDEX_MAX_RETRIES = 10
    private static final WAIT_FOR_INDEX_SLEEP_INTERVAL = 100


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
            SearchableClassMapping scm = elasticSearchContextHolder.getMappingContextByType(it)
            if (scm) {
                toRefresh << scm.queryingIndex
                toRefresh << scm.indexingIndex
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
            SearchableClassMapping scm = elasticSearchContextHolder.getMappingContextByType(it)
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

    /**
     * Deletes a mapping on an index
     * @param index The index the mapping will be deleted on
     * @param type The type which mapping will be deleted
     */
    void deleteMapping(String index, String type) {
        log.info("Deleting Elasticsearch mapping for ${index} and type ${type} ...")
        elasticSearchHelper.withElasticSearch { Client client ->
            client.admin().indices().deleteMapping(
                    new DeleteMappingRequest(index).
                            types(type)
            ).actionGet()
        }
    }

    /**
     * Creates mappings on a type
     * @param index The index where the mapping is being created
     * @param type The type where the mapping is created
     * @param elasticMapping The mapping definition
     */
    void createMapping(String index, String type, Map elasticMapping) {
        LOG.info("Creating Elasticsearch mapping for ${index} and type ${type} ...")
        elasticSearchHelper.withElasticSearch { Client client ->
            client.admin().indices().putMapping(
                    new PutMappingRequest(index)
                            .type(type)
                            .source(elasticMapping)
            ).actionGet()
        }
    }

    /**
     * Check whether a mpping exists
     * @param index The name of the index to check on
     * @param type The type which mapping is being checked
     * @return true if the mapping exists
     */
    boolean mappingExists(String index, String type) {
        elasticSearchHelper.withElasticSearch { Client client ->
            client.admin().indices().typesExists(new TypesExistsRequest([index] as String[], type)).actionGet().exists
        }
    }

    /**
     * Deletes a version of an index
     * @param index The name of the index
     * @param version the version number, if provided <index>_v<version> will be used
     */
    void deleteIndex(String index, Integer version = null) {
        index = versionIndex index, version
        LOG.info("Deleting  Elasticsearch index ${index} ...")
        elasticSearchHelper.withElasticSearch { Client client ->
            client.admin().indices().prepareDelete(index).execute().actionGet()
        }
    }

    /**
     * Creates a new index
     * @param index The name of the index
     * @param settings The index settings (ie. number of shards)
     */
    void createIndex(String index, Map settings=null) {
        LOG.debug "Creating index ${index} ..."
        elasticSearchHelper.withElasticSearch { Client client ->
            CreateIndexRequestBuilder builder = client.admin().indices().prepareCreate(index)
            if (settings) {
                builder.setSettings(settings)
            }
            builder.execute().actionGet()
        }
    }

    /**
     * Creates a new index
     * @param index The name of the index
     * @param version the version number, if provided <index>_v<version> will be used
     * @param settings The index settings (ie. number of shards)
     */
    void createIndex(String index, Integer version, Map settings=null) {
        index = versionIndex(index, version)
        createIndex(index, settings)
    }

    /**
     * Checks whether the index exists
     * @param index The name of the index
     * @param version the version number, if provided <index>_v<version> will be used
     * @return true, if the index exists
     */
    boolean indexExists(String index, Integer version = null) {
        index = versionIndex(index, version)
        elasticSearchHelper.withElasticSearch { Client client ->
            client.admin().indices().prepareExists(index).execute().actionGet().exists
        }
    }

    /**
     * Waits for the specified version of the index to exist
     * @param index The name of the index
     * @param version the version number
     */
    void waitForIndex(String index, int version) {
        int retries = WAIT_FOR_INDEX_MAX_RETRIES
        while (getLatestVersion(index) < version && retries--) {
            LOG.debug("Index ${versionIndex(index, version)} not found, sleeping for ${WAIT_FOR_INDEX_SLEEP_INTERVAL}...")
            Thread.sleep(WAIT_FOR_INDEX_SLEEP_INTERVAL)
        }
    }

    /**
     * Returns the name of the index pointed by an alias
     * @param alias The alias to be checked
     * @return the name of the index
     */
    String indexPointedBy(String alias) {
        elasticSearchHelper.withElasticSearch { Client client ->
            def index = client.admin().indices().getAliases(new GetAliasesRequest().aliases([alias] as String[])).actionGet().getAliases()?.find {
                it.value.element.alias() == alias
            }?.key
            if (!index) {
                LOG.debug("Alias ${alias} does not exist")
            }
            return index
        }
    }

    /**
     * Deletes an alias pointing to an index
     * @param alias The name of the alias
     */
    void deleteAlias(String alias) {
        elasticSearchHelper.withElasticSearch { Client client ->
            client.admin().indices().prepareAliases().removeAlias(indexPointedBy(alias), [alias] as String[]).execute().actionGet()
        }
    }

    /**
     * Makes an alias point to a new index, removing the relationship with a previous index, if any
     * @param alias the alias to be created/modified
     * @param index the index to be pointed to
     * @param version the version number, if provided <index>_v<version> will be used
     */
    void pointAliasTo(String alias, String index, Integer version = null) {
        index = versionIndex(index, version)
        LOG.debug "Creating alias ${alias}, pointing to index ${index} ..."
        String oldIndex = indexPointedBy(alias)
        elasticSearchHelper.withElasticSearch { Client client ->
            //Create atomic operation
            def aliasRequest = client.admin().indices().prepareAliases()
            if (oldIndex && oldIndex != index) {
                LOG.debug "Index used to point to ${oldIndex}, removing ..."
                aliasRequest.removeAlias(oldIndex,alias)
            }
            aliasRequest.   addAlias(index,alias)
            aliasRequest.execute().actionGet()
        }
    }

    /**
     * Checks whether an alias exists
     * @param alias the name of the alias
     * @return true if the alias exists
     */
    boolean aliasExists(String alias) {
        elasticSearchHelper.withElasticSearch { Client client ->
            client.admin().indices().prepareAliasesExist(alias).execute().actionGet().exists
        }
    }

    /**
     * Builds an index name based on a base index and a version number
     * @param index
     * @param version
     * @return <index>_v<version> if version is provided, <index> otherwise
     */
    String versionIndex(String index, Integer version = null) {
        version == null ? index : index + "_v${version}"
    }

    /**
     * Returns all the indices
     * @param prefix the prefix
     * @return a Set of index names
     */
    Set<String> getIndices() {
        elasticSearchHelper.withElasticSearch { Client client ->
            Set indices = client.admin().indices().prepareStats().execute().actionGet().indices.keySet()
        }
    }
    /**
     * Returns all the indices starting with a prefix
     * @param prefix the prefix
     * @return a Set of index names
     */
    Set<String> getIndices(String prefix) {
        Set indices = getIndices()
        if (prefix) {
            indices = indices.findAll {
                it =~ /^${prefix}/
            }
        }
        indices
    }

    /**
     * The current version of the index
     * @param index
     * @return the current version if any exists, -1 otherwise
     */
    int getLatestVersion(String index) {
        def versions = getIndices(index).collect {
            Matcher m = (it =~ /^${index}_v(\d+)$/)
            m ? m[0][1] as Integer : -1
        }.sort()
        versions ? versions.last() : -1
    }

    /**
     * The next available version for an index
     * @param index the index name
     * @return an integer representing the next version to be used for this index (ie. 10 if the latest is <index>_v<9>)
     */
    int getNextVersion(String index) {
        getLatestVersion(index) + 1
    }

    /**
     * Waits for an index to be on Yellow status
     * @param index
     * @return
     */
    def waitForIndex(index) {
        elasticSearchHelper.withElasticSearch { Client client ->
            try {
                LOG.debug("Waiting at least yellow status on ${index}")
                client.admin().cluster().prepareHealth(index)
                        .setWaitForYellowStatus()
                        .execute().actionGet()
            } catch (Exception e) {
                // ignore any exceptions due to non-existing index.
                LOG.debug('Index health', e)
            }
        }
    }

    /**
     * Waits for the cluster to be on Yellow status
     */
    void waitForClusterYellowStatus() {
        elasticSearchHelper.withElasticSearch { Client client ->
            ClusterHealthResponse response = client.admin().cluster().health(
                    new ClusterHealthRequest([] as String[]).waitForYellowStatus()).actionGet()
            LOG.debug("Cluster status: ${response.status}")
        }
    }

}

/*
 * Copyright 2002-2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.plugins.elasticsearch.index

import org.codehaus.groovy.runtime.InvokerHelper
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.bulk.BulkRequestBuilder
import org.elasticsearch.action.bulk.BulkResponse
import org.elasticsearch.client.Client
import org.elasticsearch.common.xcontent.XContentBuilder
import org.grails.plugins.elasticsearch.ElasticSearchContextHolder
import org.grails.plugins.elasticsearch.conversion.JSONDomainFactory
import org.grails.plugins.elasticsearch.exception.IndexException
import org.grails.plugins.elasticsearch.mapping.SearchableClassMapping
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.util.Assert

import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Holds objects to be indexed.
 * <br/>
 * It looks like we need to keep object references in memory until they indexed properly.
 * If indexing fails, all failed objects are retried. Still no support for max number of retries (todo)
 * NOTE: if cluster state is RED, everything will probably fail and keep retrying forever.
 * NOTE: This is shared class, so need to be thread-safe.
 */
class IndexRequestQueue {

    private static final Logger LOG = LoggerFactory.getLogger(this)

    private JSONDomainFactory jsonDomainFactory
    private ElasticSearchContextHolder elasticSearchContextHolder
    private Client elasticSearchClient

    /**
     * A map containing the pending index requests.
     */
    private Map<IndexEntityKey, Object> indexRequests = [:]

    /**
     * A set containing the pending delete requests.
     */
    private Set<IndexEntityKey> deleteRequests = []

    private ConcurrentLinkedDeque<OperationBatch> operationBatch = new ConcurrentLinkedDeque<OperationBatch>()

    void setJsonDomainFactory(JSONDomainFactory jsonDomainFactory) {
        this.jsonDomainFactory = jsonDomainFactory
    }

    void setElasticSearchContextHolder(ElasticSearchContextHolder elasticSearchContextHolder) {
        this.elasticSearchContextHolder = elasticSearchContextHolder
    }

    void setElasticSearchClient(Client elasticSearchClient) {
        this.elasticSearchClient = elasticSearchClient
    }

    void addIndexRequest(instance) {
        addIndexRequest(instance, null)
    }

    void addIndexRequest(instance, Serializable id) {
        synchronized (this) {
            IndexEntityKey key = id == null ? indexEntityKeyFromInstance(instance) :
                    new IndexEntityKey(id.toString(), instance.getClass())
            indexRequests.put(key, instance)
        }
    }

    void addDeleteRequest(instance) {
        synchronized (this) {
            deleteRequests.add(indexEntityKeyFromInstance(instance))
        }
    }

    IndexEntityKey indexEntityKeyFromInstance(instance) {
        def clazz = instance.getClass()
        SearchableClassMapping scm = elasticSearchContextHolder.getMappingContextByType(clazz)
        Assert.notNull(scm, "Class $clazz is not a searchable domain class.")
        def id = (InvokerHelper.invokeMethod(instance, 'getId', null)).toString()
        new IndexEntityKey(id, clazz)
    }

    XContentBuilder toJSON(instance) {
        try {
            return jsonDomainFactory.buildJSON(instance)
        } catch (Throwable t) {
            throw new IndexException("Failed to marshall domain instance [$instance]", t)
        }
    }

    /**
     * Execute pending requests and clear both index & delete pending queues.
     *
     * @return Returns an OperationBatch instance which is a listener to the last executed bulk operation. Returns NULL
     *         if there were no operations done on the method call.
     */
    void executeRequests() {
        Map<IndexEntityKey, Object> toIndex = [:]
        Set<IndexEntityKey> toDelete = []

        cleanOperationBatchList()

        // Copy existing queue to ensure we are interfering with incoming requests.
        synchronized (this) {
            toIndex.putAll(indexRequests)
            toDelete.addAll(deleteRequests)
            indexRequests.clear()
            deleteRequests.clear()
        }

        // If there are domain instances that are both in the index requests & delete requests list,
        // they are directly deleted.
        toIndex.keySet().removeAll(toDelete)

        // If there is nothing in the queues, just stop here
        if (toIndex.isEmpty() && toDelete.empty) {
            return
        }

        BulkRequestBuilder bulkRequestBuilder = elasticSearchClient.prepareBulk()

        toIndex.each { key, value ->
            SearchableClassMapping scm = elasticSearchContextHolder.getMappingContextByType(key.clazz)

            def parentMapping = scm.propertiesMapping.find { it.parent }

            try {
                XContentBuilder json = toJSON(value)

                def index = elasticSearchClient.prepareIndex()
                        .setIndex(scm.indexingIndex)
                        .setType(scm.elasticTypeName)
                        .setId(key.id) // TODO : Composite key ?
                        .setSource(json)
                if (parentMapping) {
                    index = index.setParent(value."${parentMapping.propertyName}".id?.toString())
                }

                bulkRequestBuilder.add(index)
                if (LOG.isDebugEnabled()) {
                    try {
                        LOG.debug("Indexing $key.clazz (index: $scm.indexingIndex , type: $scm.elasticTypeName) of id $key.id and source ${json.string()}")
                    } catch (IOException e) {
                    }
                }
            } catch (Exception e) {
                LOG.error("Error Indexing $key.clazz (index: $scm.indexingIndex , type: $scm.elasticTypeName) of id $key.id", e)
            }
        }

        // Execute delete requests
        toDelete.each {
            SearchableClassMapping scm = elasticSearchContextHolder.getMappingContextByType(it.clazz)
            if (LOG.isDebugEnabled()) {
                LOG.debug("Deleting object from index $scm.indexingIndex and type $scm.elasticTypeName and ID $it.id")
            }
            bulkRequestBuilder.add(
                    elasticSearchClient.prepareDelete()
                            .setIndex(scm.indexingIndex)
                            .setType(scm.elasticTypeName)
                            .setId(it.id)
            )
        }

        if (bulkRequestBuilder.numberOfActions() > 0) {
            OperationBatch completeListener = new OperationBatch(0, toIndex, toDelete)
            operationBatch.add(completeListener)
            try {
                bulkRequestBuilder.execute().addListener(completeListener)
            } catch (Exception e) {
                throw new IndexException("Failed to index/delete ${bulkRequestBuilder.numberOfActions()}", e)
            }
        }
    }

    void waitComplete() {
        LOG.debug('IndexRequestQueue.waitComplete() called')
        List<OperationBatch> clone = []
        synchronized (this) {
            clone.addAll(operationBatch)
            operationBatch.clear()
        }
        clone.each { it.waitComplete() }
    }

    private void cleanOperationBatchList() {
        synchronized (this) {
            for (Iterator<OperationBatch> it = operationBatch.iterator(); it.hasNext();) {
                OperationBatch current = it.next()
                if (current.complete) {
                    it.remove()
                }
            }
        }
        LOG.debug('OperationBatchList cleaned')
    }

    class OperationBatch implements ActionListener<BulkResponse> {

        private AtomicInteger attempts
        private Map<IndexEntityKey, Object> toIndex
        private Set<IndexEntityKey> toDelete
        private CountDownLatch synchronizedCompletion = new CountDownLatch(1)

        OperationBatch(int attempts, Map<IndexEntityKey, Object> toIndex, Set<IndexEntityKey> toDelete) {
            this.attempts = new AtomicInteger(attempts)
            this.toIndex = toIndex
            this.toDelete = toDelete
        }

        boolean isComplete() {
            synchronizedCompletion.count == 0
        }

        void waitComplete() {
            waitComplete(null)
        }

        /**
         * Wait for the operation to complete. Use this method to synchronize the application with the last ES operation.
         *
         * @param msTimeout A maximum timeout (in milliseconds) before the wait method returns, whether the operation has been completed or not.
         *                  Default value is 5000 milliseconds
         */
        void waitComplete(Integer msTimeout) {
            msTimeout = msTimeout == null ? 5000 : msTimeout

            try {
                if (!synchronizedCompletion.await(msTimeout, TimeUnit.MILLISECONDS)) {
                    LOG.warn("OperationBatchList.waitComplete() timed out after ${msTimeout.toString()} ms")
                }
            } catch (InterruptedException ie) {
                LOG.warn('OperationBatchList.waitComplete() interrupted')
            }
        }

        void fireComplete() {
            synchronizedCompletion.countDown()
        }

        void onResponse(BulkResponse bulkResponse) {
            bulkResponse.getItems().each {
                boolean removeFromQueue = !it.failed || it.failureMessage.contains('UnavailableShardsException')
                // On shard failure, do not re-push.
                if (removeFromQueue) {
                    // remove successful OR fatal ones.
                    Class<?> entityClass = elasticSearchContextHolder.findMappedClassByElasticType(it.type)
                    if (entityClass == null) {
                        LOG.error("Elastic type [${it.type}] is not mapped.")
                        return
                    }
                    IndexEntityKey key = new IndexEntityKey(it.id, entityClass)
                    toIndex.remove(key)
                    toDelete.remove(key)
                }
                if (it.failed) {
                    LOG.error("Failed bulk item: $it.failureMessage")
                }
            }
            if (!toIndex.isEmpty() || !toDelete.isEmpty()) {
                push()
            } else {
                fireComplete()
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Batch complete: ${bulkResponse.getItems().length} actions.")
                }
            }
        }

        void onFailure(Throwable e) {
            // Everything failed. Re-push all.
            LOG.error('Bulk request failure', e)
            def remainingAttempts = attempts.getAndDecrement()
            if (remainingAttempts > 0) {
                LOG.info("Retrying failed bulk request ($remainingAttempts attempts remaining)")
                push()
            } else {
                LOG.info("Aborting bulk request - no attempts remain)")
            }
        }

        /**
         * Push specified entities to be retried.
         */
        void push() {
            LOG.debug("Pushing retry: ${toIndex.size()} indexing, ${toDelete.size()} deletes.")
            toIndex.each { key, value ->
                synchronized (this) {
                    if (!indexRequests.containsKey(key)) {
                        // Do not overwrite existing stuff in the queue.
                        indexRequests.put(key, value)
                    }
                }
            }
            toDelete.each {
                synchronized (this) {
                    if (!deleteRequests.contains(it)) {
                        deleteRequests.add(it)
                    }
                }
            }
            executeRequests()
        }
    }
}

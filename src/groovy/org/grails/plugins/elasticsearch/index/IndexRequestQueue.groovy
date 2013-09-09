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

import org.apache.log4j.Logger
import org.codehaus.groovy.runtime.InvokerHelper
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.bulk.BulkItemResponse
import org.elasticsearch.action.bulk.BulkRequestBuilder
import org.elasticsearch.action.bulk.BulkResponse
import org.elasticsearch.client.Client
import org.elasticsearch.common.xcontent.XContentBuilder
import org.grails.datastore.gorm.support.DatastorePersistenceContextInterceptor
import org.grails.plugins.elasticsearch.ElasticSearchContextHolder
import org.grails.plugins.elasticsearch.conversion.JSONDomainFactory
import org.grails.plugins.elasticsearch.exception.IndexException
import org.grails.plugins.elasticsearch.mapping.SearchableClassMapping
import org.springframework.beans.factory.InitializingBean

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Holds objects to be indexed.
 * <br/>
 * It looks like we need to keep object references in memory until they indexed properly.
 * If indexing fails, all failed objects are retried. Still no support for max number of retries (todo)
 * NOTE: if cluster state is RED, everything will probably fail and keep retrying forever.
 * NOTE: This is shared class, so need to be thread-safe.
 */
public class IndexRequestQueue implements InitializingBean {

    private static final Logger LOG = Logger.getLogger(IndexRequestQueue.class)

    private JSONDomainFactory jsonDomainFactory
    private ElasticSearchContextHolder elasticSearchContextHolder
    private Client elasticSearchClient
    private DatastorePersistenceContextInterceptor persistenceInterceptor

    /**
     * A map containing the pending index requests.
     */
    Map<IndexEntityKey, Object> indexRequests = new HashMap<IndexEntityKey, Object>()

    /**
     * A set containing the pending delete requests.
     */
    Set<IndexEntityKey> deleteRequests = new HashSet<IndexEntityKey>()

    List<OperationBatch> operationBatchList = new LinkedList<OperationBatch>()

    /**
     * No-args constructor.
     */
    public IndexRequestQueue() {
    }

    public void setJsonDomainFactory(JSONDomainFactory jsonDomainFactory) {
        this.jsonDomainFactory = jsonDomainFactory
    }

    public void setElasticSearchContextHolder(ElasticSearchContextHolder elasticSearchContextHolder) {
        this.elasticSearchContextHolder = elasticSearchContextHolder
    }

    public void setElasticSearchClient(Client elasticSearchClient) {
        this.elasticSearchClient = elasticSearchClient
    }

    public void setPersistenceInterceptor(DatastorePersistenceContextInterceptor persistenceInterceptor) {
        this.persistenceInterceptor = persistenceInterceptor
    }

    /**
     */
    public void afterPropertiesSet() throws Exception {
        persistenceInterceptor.setReadOnly()
    }

    public void addIndexRequest(Object instance) {
        addIndexRequest(instance, null)
    }

    public void addIndexRequest(Object instance, Serializable id) {
        synchronized (this) {
            IndexEntityKey key = id == null ? new IndexEntityKey(instance) :
                new IndexEntityKey(id.toString(), instance.getClass())
            indexRequests.put(key, instance)
        }
    }

    public void addDeleteRequest(Object instance) {
        synchronized (this) {
            deleteRequests.add(new IndexEntityKey(instance))
        }
    }

    public XContentBuilder toJSON(Object instance) {
        try {
            return jsonDomainFactory.buildJSON(instance)
        } catch (Throwable t) {
            throw new IndexException("Failed to marshall domain instance [" + instance + "]", t)
        }
    }

    /**
     * Execute pending requests and clear both index & delete pending queues.
     *
     * @return Returns an OperationBatch instance which is a listener to the last executed bulk operation. Returns NULL
     *         if there were no operations done on the method call.
     */
    public OperationBatch executeRequests() {
        Map<IndexEntityKey, Object> toIndex = new LinkedHashMap<IndexEntityKey, Object>()
        Set<IndexEntityKey> toDelete = new HashSet<IndexEntityKey>()

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
        if (toIndex.isEmpty() && toDelete.isEmpty()) {
            return null
        }

        BulkRequestBuilder bulkRequestBuilder = elasticSearchClient.prepareBulk()
        //bulkRequestBuilder.setRefresh(true)

        // Execute index requests
        for (Map.Entry<IndexEntityKey, Object> entry : toIndex.entrySet()) {
            SearchableClassMapping scm = elasticSearchContextHolder.getMappingContextByType(entry.getKey().getClazz())
            persistenceInterceptor.init()
            try {
                Object entity = entry.getValue()

                XContentBuilder json = toJSON(entity)

                bulkRequestBuilder.add(
                        elasticSearchClient.prepareIndex()
                                .setIndex(scm.getIndexName())
                                .setType(scm.getElasticTypeName())
                                .setId(entry.getKey().getId()) // TODO : Composite key ?
                                .setSource(json)
                )
                if (LOG.isDebugEnabled()) {
                    try {
                        LOG.debug("Indexing " + entry.getKey().getClazz() + "(index:" + scm.getIndexName() + ",type:" + scm.getElasticTypeName() +
                                ") of id " + entry.getKey().getId() + " and source " + json.string())
                    } catch (IOException e) {
                    }
                }
            } finally {
                persistenceInterceptor.destroy()
            }
        }

        // Execute delete requests
        for (IndexEntityKey key : toDelete) {
            SearchableClassMapping scm = elasticSearchContextHolder.getMappingContextByType(key.getClazz())
            if (LOG.isDebugEnabled()) {
                LOG.debug("Deleting object from index " + scm.getIndexName() + " and type " + scm.getElasticTypeName() + " and ID " + key.getId())
            }
            bulkRequestBuilder.add(
                    elasticSearchClient.prepareDelete()
                            .setIndex(scm.getIndexName())
                            .setType(scm.getElasticTypeName())
                            .setId(key.getId())
            )
        }

        // Perform bulk request
        OperationBatch completeListener = null
        if (bulkRequestBuilder.numberOfActions() > 0) {
            completeListener = new OperationBatch(0, toIndex, toDelete)
            operationBatchList.add(completeListener)
            try {
                bulkRequestBuilder.execute().addListener(completeListener)
            } catch (Exception e) {
                throw new IndexException("Failed to index/delete " + bulkRequestBuilder.numberOfActions(), e)
            }
        }

        return completeListener
    }

    public void waitComplete() {
        LOG.debug("IndexRequestQueue.waitComplete() called")
        List<OperationBatch> clone = new LinkedList<OperationBatch>()
        synchronized (this) {
            clone.addAll(operationBatchList)
            operationBatchList.clear()
        }
        for (OperationBatch op : clone) {
            op.waitComplete()
        }
    }

    private void cleanOperationBatchList() {
        synchronized (this) {
            for (Iterator<OperationBatch> it = operationBatchList.iterator(); it.hasNext();) {
                OperationBatch current = it.next()
                if (current.isComplete()) {
                    it.remove()
                }
            }
        }
        LOG.debug("OperationBatchList cleaned")
    }

    class OperationBatch implements ActionListener<BulkResponse> {

        private int attempts
        private Map<IndexEntityKey, Object> toIndex
        private Set<IndexEntityKey> toDelete
        private CountDownLatch synchronizedCompletion = new CountDownLatch(1)

        OperationBatch(int attempts, Map<IndexEntityKey, Object> toIndex, Set<IndexEntityKey> toDelete) {
            this.attempts = attempts
            this.toIndex = toIndex
            this.toDelete = toDelete
        }

        public boolean isComplete() {
            return synchronizedCompletion.getCount() == 0
        }

        public void waitComplete() {
            waitComplete(null)
        }

        /**
         * Wait for the operation to complete. Use this method to synchronize the application with the last ES operation.
         *
         * @param msTimeout A maximum timeout (in milliseconds) before the wait method returns, whether the operation has been completed or not.
         *                  Default value is 5000 milliseconds
         */
        public void waitComplete(Integer msTimeout) {
            msTimeout = msTimeout == null ? 5000 : msTimeout

            try {
                if (!synchronizedCompletion.await(msTimeout, TimeUnit.MILLISECONDS)) {
                    LOG.warn("OperationBatchList.waitComplete() timed out after " + msTimeout.toString() + "ms")
                }
            } catch (InterruptedException ie) {
                LOG.warn("OperationBatchList.waitComplete() interrupted")
            }
        }

        public void fireComplete() {
            synchronizedCompletion.countDown()
        }

        public void onResponse(BulkResponse bulkResponse) {
            for (BulkItemResponse item : bulkResponse.getItems()) {
                boolean removeFromQueue = !item.isFailed() || (item.getFailureMessage().indexOf("UnavailableShardsException") >= 0)
                // On shard failure, do not re-push.
                if (removeFromQueue) {
                    // remove successful OR fatal ones.
                    Class<?> entityClass = elasticSearchContextHolder.findMappedClassByElasticType(item.getType())
                    if (entityClass == null) {
                        LOG.error("Elastic type [" + item.getType() + "] is not mapped.")
                        continue
                    }
                    IndexEntityKey key = new IndexEntityKey(item.getId(), entityClass)
                    toIndex.remove(key)
                    toDelete.remove(key)
                }
                if (item.isFailed()) {
                    LOG.error("Failed bulk item: " + item.getFailureMessage())
                }
            }
            if (!toIndex.isEmpty() || !toDelete.isEmpty()) {
                push()
            } else {
                fireComplete()
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Batch complete: " + bulkResponse.getItems().length + " actions.")
                }
            }
        }

        public void onFailure(Throwable e) {
            // Everything failed. Re-push all.
            LOG.error("Bulk request failure", e)
            push()
        }

        /**
         * Push specified entities to be retried.
         */
        public void push() {
            LOG.debug("Pushing retry: " + toIndex.size() + " indexing, " + toDelete.size() + " deletes.")
            for (Map.Entry<IndexEntityKey, Object> entry : toIndex.entrySet()) {
                synchronized (this) {
                    if (!indexRequests.containsKey(entry.getKey())) {
                        // Do not overwrite existing stuff in the queue.
                        indexRequests.put(entry.getKey(), entry.getValue())
                    }
                }
            }
            for (IndexEntityKey key : toDelete) {
                synchronized (this) {
                    if (!deleteRequests.contains(key)) {
                        deleteRequests.add(key)
                    }
                }
            }

            executeRequests()
        }
    }

    class IndexEntityKey implements Serializable {

        /**
         * stringified id.
         */
        private final String id
        private final Class clazz

        IndexEntityKey(String id, Class clazz) {
            this.id = id
            this.clazz = clazz
        }

        IndexEntityKey(Object instance) {
            this.clazz = instance.getClass()
            SearchableClassMapping scm = elasticSearchContextHolder.getMappingContextByType(this.clazz)
            if (scm == null) {
                throw new IllegalArgumentException("Class " + clazz + " is not a searchable domain class.")
            }
            this.id = (InvokerHelper.invokeMethod(instance, "getId", null)).toString()
        }

        public String getId() {
            return id
        }

        public Class getClazz() {
            return clazz
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true
            if (o == null || getClass() != o.getClass()) return false

            IndexEntityKey that = (IndexEntityKey) o

            if (!clazz.equals(that.clazz)) return false
            if (!id.equals(that.id)) return false

            return true
        }

        @Override
        public int hashCode() {
            int result = id.hashCode()
            result = 31 * result + clazz.hashCode()
            return result
        }

        @Override
        public String toString() {
            return "IndexEntityKey{" +
                    "id=" + id +
                    ", clazz=" + clazz +
                    '}'
        }
    }
}
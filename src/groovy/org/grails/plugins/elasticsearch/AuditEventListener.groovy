/*
 * Copyright 2002-2010 the original author or authors.
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
package org.grails.plugins.elasticsearch

import org.apache.log4j.Logger
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.engine.event.*
import org.grails.plugins.elasticsearch.index.IndexRequestQueue
import org.springframework.context.ApplicationEvent
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationAdapter
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * Listen to GORM events.
 */
class AuditEventListener extends AbstractPersistenceEventListener {

    private static final Logger LOG = Logger.getLogger(AuditEventListener)

    ElasticSearchContextHolder elasticSearchContextHolder

    IndexRequestQueue indexRequestQueue

    /** List of pending objects to reindex. */
    private static ThreadLocal<Map> pendingObjects = new ThreadLocal<Map>()

    /** List of pending object to delete */
    private static ThreadLocal<Map> deletedObjects = new ThreadLocal<Map>()

    public AuditEventListener(Datastore datastore) {
        super(datastore)
    }

    def registerMySynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                if (sync instanceof IndexSynchronization) {
                    // already registered.
                    return
                }
            }
            TransactionSynchronizationManager.registerSynchronization(new IndexSynchronization(indexRequestQueue))
        }
    }

    /**
     * Push object to index. Save as pending if transaction is not committed yet.
     */
    public def pushToIndex(entity) {
        // Register transaction synchronization
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // Save object as pending
            def objs = pendingObjects.get()
            if (!objs) {
                objs = [:]
                pendingObjects.set(objs)
            }

            def key = new EntityKey(entity.class.simpleName, entity.id)
            if (deletedObjects.get()) {
                deletedObjects.get().remove(key)
            }
            objs[key] = entity
            registerMySynchronization()

        } else {
            indexRequestQueue.addIndexRequest(entity)
            indexRequestQueue.executeRequests()
        }
    }

    public def pushToDelete(entity) {
        // Register transaction synchronization
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // Add to list of deleted
            def objs = deletedObjects.get()
            if (!objs) {
                objs = [:]
                deletedObjects.set(objs)
            }

            def key = new EntityKey(entity.class.simpleName, entity.id)
            if (pendingObjects.get()) {
                pendingObjects.get().remove(key)
            }
            objs[key] = entity
            registerMySynchronization()

        } else {
            indexRequestQueue.addIndexRequest(entity)
            indexRequestQueue.executeRequests()
        }
    }

    @Override
    protected void onPersistenceEvent(AbstractPersistenceEvent event) {
        if (event instanceof PostInsertEvent) {
            onPostInsert(event)
        } else if (event instanceof PostUpdateEvent) {
            onPostUpdate(event)
        } else if (event instanceof PostDeleteEvent) {
            onPostDelete(event)
        }
    }

    @Override
    boolean supportsEventType(Class<? extends ApplicationEvent> aClass) {
        [PostInsertEvent, PostUpdateEvent, PostDeleteEvent].any() { it.isAssignableFrom(aClass) }
    }

    public void onPostInsert(PostInsertEvent event) {
        def entity = event.entityAccess.entity
        if (elasticSearchContextHolder.isRootClass(entity.class)) {
            pushToIndex(entity)
        }
    }

    public void onPostUpdate(PostUpdateEvent event) {
        def entity = event.entityAccess.entity
        if (elasticSearchContextHolder.isRootClass(entity.class)) {
            pushToIndex(entity)
        }
    }

    public void onPostDelete(PostDeleteEvent event) {
        def entity = event.entityAccess.entity
        if (elasticSearchContextHolder.isRootClass(entity.class)) {
            pushToDelete(entity)
        }
    }

    public void setElasticSearchContextHolder(ElasticSearchContextHolder elasticSearchContextHolder) {
        this.elasticSearchContextHolder = elasticSearchContextHolder
    }

    void setIndexRequestQueue(IndexRequestQueue indexRequestQueue) {
        this.indexRequestQueue = indexRequestQueue
    }

    static class EntityKey {

        private String entityName
        private Serializable id


        EntityKey(String entityName, Serializable id) {
            this.entityName = entityName
            this.id = id
        }


        boolean equals(o) {
            if (this.is(o)) return true
            if (getClass() != o.class) return false

            EntityKey entityKey = (EntityKey) o

            if (entityName != entityKey.entityName) return false
            if (id != entityKey.id) return false

            return true
        }

        int hashCode() {
            int result
            result = (entityName != null ? entityName.hashCode() : 0)
            result = 31 * result + (id != null ? id.hashCode() : 0)
            return result
        }
    }

    /**
     * Helper class for indexing objects on transaction commit.
     */
    class IndexSynchronization extends TransactionSynchronizationAdapter {

        private IndexRequestQueue indexRequestQueue

        IndexSynchronization(IndexRequestQueue indexRequestQueue) {
            this.indexRequestQueue = indexRequestQueue
        }

        /**
         * Fired on transaction completion (commit or rollback).
         * @param status transaction completion status
         */
        def void afterCompletion(int status) {
            def objsToIndex = pendingObjects.get()
            def objsToDelete = deletedObjects.get()
            switch (status) {
                case STATUS_COMMITTED:
                    if (objsToIndex && objsToDelete) {
                        objsToIndex.keySet().removeAll(objsToDelete.keySet())
                    }

                    if (objsToIndex) {
                        for (def obj : objsToIndex.values()) {
                            indexRequestQueue.addIndexRequest(obj)
                        }
                    }
                    if (objsToDelete) {
                        for (def obj : objsToDelete.values()) {
                            indexRequestQueue.addDeleteRequest(obj)
                        }
                    }

                    // flush to index.
                    indexRequestQueue.executeRequests()

                    break
                case STATUS_ROLLED_BACK:
                    LOG.debug "Rollbacking ${objsToIndex ? objsToIndex.size() : 0} objs."
                    break
                default:
                    LOG.error "Unknown transaction state."
            }

            // Clear objs
            pendingObjects.set(null)
            deletedObjects.set(null)
        }
    }
}
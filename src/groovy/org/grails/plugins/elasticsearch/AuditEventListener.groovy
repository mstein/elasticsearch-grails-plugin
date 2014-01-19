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

import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.engine.event.*
import org.grails.plugins.elasticsearch.index.IndexRequestQueue
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEvent
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * Listen to GORM events.
 */
class AuditEventListener extends AbstractPersistenceEventListener {

    private static final Logger logger = LoggerFactory.getLogger(this)

    ElasticSearchContextHolder elasticSearchContextHolder

    IndexRequestQueue indexRequestQueue

    /** List of pending objects to reindex. */
    static ThreadLocal<Map> pendingObjects = new ThreadLocal<Map>()

    /** List of pending object to delete */
    static ThreadLocal<Map> deletedObjects = new ThreadLocal<Map>()

    AuditEventListener(Datastore datastore) {
        super(datastore)
    }

    void registerMySynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                if (sync instanceof IndexSynchronization) {
                    // already registered.
                    return
                }
            }
            TransactionSynchronizationManager.registerSynchronization(new IndexSynchronization(indexRequestQueue, this))
        }
    }

    /**
     * Push object to index. Save as pending if transaction is not committed yet.
     */
    void pushToIndex(entity) {
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

    void pushToDelete(entity) {
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
            indexRequestQueue.addDeleteRequest(entity)
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

    void onPostInsert(PostInsertEvent event) {
        def entity = getEventEntity(event)
        if (!entity) {
            logger.warn('Received a PostInsertEvent with no entity')
            return
        }
        if (elasticSearchContextHolder.isRootClass(entity.class)) {
            pushToIndex(entity)
        }
    }

    void onPostUpdate(PostUpdateEvent event) {
        def entity = getEventEntity(event)
        if (!entity) {
            logger.warn('Received a PostUpdateEvent with no entity')
            return
        }
        if (elasticSearchContextHolder.isRootClass(entity.class)) {
            pushToIndex(entity)
        }
    }

    void onPostDelete(PostDeleteEvent event) {
        def entity = getEventEntity(event)
        if (!entity) {
            logger.warn('Received a PostDeleteEvent with no entity')
            return
        }
        if (elasticSearchContextHolder.isRootClass(entity.class)) {
            pushToDelete(entity)
        }
    }

    Map getPendingObjects() {
        pendingObjects.get()
    }

    Map getDeletedObjects() {
        deletedObjects.get()
    }

    void clearPendingObjects() {
        pendingObjects.remove()
    }

    void clearDeletedObjects() {
        deletedObjects.remove()
    }

    private def getEventEntity(AbstractPersistenceEvent event) {
        if (event.entityAccess) {
            return event.entityAccess.entity
        }

        event.entityObject
    }
}

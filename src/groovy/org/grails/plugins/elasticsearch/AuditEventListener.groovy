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

import org.hibernate.event.PostCollectionUpdateEventListener
import org.hibernate.event.PostCollectionUpdateEvent
import org.hibernate.event.DeleteEvent
import org.hibernate.event.FlushEventListener
import org.hibernate.event.FlushEvent
import org.hibernate.event.PostInsertEventListener
import org.hibernate.event.PostInsertEvent
import org.hibernate.event.PostUpdateEventListener
import org.hibernate.event.PostUpdateEvent
import org.hibernate.event.PostDeleteEventListener
import org.hibernate.event.PostDeleteEvent
import org.codehaus.groovy.grails.orm.hibernate.events.SaveOrUpdateEventListener
import org.springframework.context.ApplicationContextAware
import org.springframework.context.ApplicationContext
import org.grails.plugins.elasticsearch.index.IndexRequestQueue
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionSynchronizationAdapter
import org.apache.log4j.Logger
import org.springframework.orm.hibernate3.HibernateTemplate
import org.hibernate.SessionFactory
import org.springframework.orm.hibernate3.HibernateCallback
import org.hibernate.Session
import org.springframework.transaction.support.TransactionSynchronization

/**
 * Listen to Hibernate events.
 */
class AuditEventListener extends SaveOrUpdateEventListener implements PostCollectionUpdateEventListener,
                                                                      PostInsertEventListener, PostUpdateEventListener,
                                                                      PostDeleteEventListener,
                                                                      FlushEventListener,
                                                                      ApplicationContextAware
                                                                        {
    /** Logger */
    private static final Logger LOG = Logger.getLogger(AuditEventListener.class)

    /** ES context */
    def elasticSearchContextHolder

    /** Spring application context */
    def applicationContext

    /** List of pending objects to reindex. */
    private static ThreadLocal<Map> pendingObjects = new ThreadLocal<Map>()

    /** List of pending object to delete */
    private static ThreadLocal<Map> deletedObjects = new ThreadLocal<Map>()

    /**
     * Index & Delete requests are execute once per flush.
     * Before a flush event, the requests are store in callsBuffer and then executed once onFlush() is called.
     */
    IndexRequestQueue getIndexRequestQueue() {
        applicationContext.getBean("indexRequestQueue", IndexRequestQueue)
    }


    def registerMySynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            for(TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                if (sync instanceof IndexSynchronization) {
                    // already registered.
                    return
                }
            }
            TransactionSynchronizationManager.registerSynchronization(new IndexSynchronization())
        }
    }


    /**
     * Push object to index. Save as pending if transaction is not committed yet.
     * @param obj object to index
     * @param id assigned identifier (optional)
     */
    def pushToIndex(entityName, id, obj) {
        // Register transaction synchronization
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // Save object as pending
            def objs = pendingObjects.get()
            if (!objs) {
                objs = [:]
                pendingObjects.set(objs)
            }

            def key = new EntityKey(entityName, id)
            if (deletedObjects.get()) {
                deletedObjects.get().remove(key)
            }
            objs[key] = obj
            registerMySynchronization()

        } else {
            // No transaction - Fire immediately.
            indexRequestQueue.addIndexRequest(obj)
        }

    }

    def pushToDelete(entityName, id, obj) {
        // Register transaction synchronization
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // Add to list of deleted
            def objs = deletedObjects.get()
            if (!objs) {
                objs = [:]
                deletedObjects.set(objs)
            }

            def key = new EntityKey(entityName, id)
            if (pendingObjects.get()) {
                pendingObjects.get().remove(key)
            }
            objs[key] = obj
            registerMySynchronization()

        } else {
            // No transaction - Fire immediately.
            indexRequestQueue.addDeleteRequest(obj)
        }
    }


    void onPostUpdateCollection(PostCollectionUpdateEvent postCollectionUpdateEvent) {
        def clazz = postCollectionUpdateEvent.affectedOwnerOrNull?.class
        // Todo : optimize reindexing requests
        if (elasticSearchContextHolder.isRootClass(clazz)) {
            pushToIndex(postCollectionUpdateEvent.affectedOwnerEntityName,
                    postCollectionUpdateEvent.affectedOwnerIdOrNull,
                    postCollectionUpdateEvent.affectedOwnerOrNull)
        }
    }

    void onPostInsert(PostInsertEvent event) {
        def clazz = event.entity?.class
        if (elasticSearchContextHolder.isRootClass(clazz)) {
            pushToIndex(event.persister.entityName, event.id, event.entity)
        }
    }

    void onPostUpdate(PostUpdateEvent event) {
        def clazz = event.entity?.class
        if (elasticSearchContextHolder.isRootClass(clazz)) {
            pushToIndex(event.persister.entityName, event.id, event.entity)
        }
    }

    void onPostDelete(PostDeleteEvent event) {
        def clazz = event.entity?.class
        if (elasticSearchContextHolder.isRootClass(clazz)) {
            pushToDelete(event.persister.entityName, event.id, event.entity)
        }
    }

    void onDelete(DeleteEvent deleteEvent, Set set) {
        // TODO : for cascade delete
    }

    void onFlush(FlushEvent flushEvent) {
        // When a flush occurs, execute the pending requests in the buffer (the buffer is cleared automatically)
        indexRequestQueue.executeRequests(flushEvent.session)
    }

    void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext
    }


    static class EntityKey {

        private String entityName
        private Serializable id


        EntityKey(String entityName, Serializable id) {
            this.entityName = entityName
            this.id = id
        }


        boolean equals(o) {
            if (this.is(o)) return true;
            if (getClass() != o.class) return false;

            EntityKey entityKey = (EntityKey) o;

            if (entityName != entityKey.entityName) return false;
            if (id != entityKey.id) return false;

            return true;
        }

        int hashCode() {
            int result;
            result = (entityName != null ? entityName.hashCode() : 0);
            result = 31 * result + (id != null ? id.hashCode() : 0);
            return result;
        }
    }


    /**
     * Helper class for indexing objects on transaction commit.
     */
    class IndexSynchronization extends TransactionSynchronizationAdapter {

        /**
         * Fired on transaction completion (commit or rollback).
         * @param status transaction completion status
         */
        def void afterCompletion(int status) {
            def objsToIndex = pendingObjects.get()
            def objsToDelete = deletedObjects.get()
            switch (status) {
                case STATUS_COMMITTED:
                    LOG.debug "Committing ${objsToIndex ? objsToIndex.size() : 0} objs."
                    if (objsToIndex && objsToDelete) {
                        objsToIndex.keySet().removeAll(objsToDelete.keySet())
                    }

                    // better use new session,
                    // otherwise http://jira.codehaus.org/browse/GRAILS-4453
                    // (transactions are supposed to be managed
                    // by individual event listeners)
                    HibernateTemplate template = new HibernateTemplate(applicationContext.getBean('sessionFactory', SessionFactory.class))
                    def indexRequestQueue = getIndexRequestQueue()
                    template.executeWithNewSession(new HibernateCallback() {
                        Object doInHibernate(Session session) {
                            if (objsToIndex) {
                                for (def obj: objsToIndex.values()) {
                                    indexRequestQueue.addIndexRequest(obj)
                                }
                            }
                            if (objsToDelete) {
                                for (def obj: objsToDelete.values()) {
                                    indexRequestQueue.addDeleteRequest(obj)
                                }
                            }
                            null
                        }
                    })

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

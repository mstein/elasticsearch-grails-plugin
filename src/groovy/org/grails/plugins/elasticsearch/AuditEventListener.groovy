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

class AuditEventListener extends SaveOrUpdateEventListener implements PostCollectionUpdateEventListener,
                                                                      PostInsertEventListener, PostUpdateEventListener,
                                                                      PostDeleteEventListener,
                                                                      FlushEventListener,
                                                                      ApplicationContextAware
                                                                        {

    def elasticSearchContextHolder
    
    def applicationContext

    /**
     * Index & Delete requests are execute once per flush.
     * Before a flush event, the requests are store in callsBuffer and then executed once onFlush() is called.
     */
    IndexRequestQueue getIndexRequestQueue() {
        applicationContext.getBean("indexRequestQueue", IndexRequestQueue)
    }

    void onPostUpdateCollection(PostCollectionUpdateEvent postCollectionUpdateEvent) {
        def clazz = postCollectionUpdateEvent.affectedOwnerOrNull?.class
        // Todo : optimize reindexing requests
        if (elasticSearchContextHolder.isRootClass(clazz)) {
            indexRequestQueue.addIndexRequest(postCollectionUpdateEvent.affectedOwnerOrNull)
        }
    }

    void onPostInsert(PostInsertEvent event) {
        def clazz = event.entity?.class
        if (elasticSearchContextHolder.isRootClass(clazz)) {
            indexRequestQueue.addIndexRequest(event.entity, event.id)
        }
    }

    void onPostUpdate(PostUpdateEvent event) {
        def clazz = event.entity?.class
        if (elasticSearchContextHolder.isRootClass(clazz)) {
            indexRequestQueue.addIndexRequest(event.entity)
        }
    }

    void onPostDelete(PostDeleteEvent event) {
        def clazz = event.entity?.class
        if (elasticSearchContextHolder.isRootClass(clazz)) {
            indexRequestQueue.addDeleteRequest(event.entity)
        }
    }

    void onDelete(DeleteEvent deleteEvent, Set set) {
        // TODO : for cascade delete
    }

    void onFlush(FlushEvent flushEvent) {
        // When a flush occurs, execute the pending requests in the buffer (the buffer is cleared automatically)
        indexRequestQueue.executeRequests()
    }

    void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext
    }


}

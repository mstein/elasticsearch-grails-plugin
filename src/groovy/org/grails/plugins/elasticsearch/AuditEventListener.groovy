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
import org.hibernate.event.SaveOrUpdateEvent
import org.codehaus.groovy.grails.orm.hibernate.events.SaveOrUpdateEventListener
import org.hibernate.event.DeleteEventListener
import org.hibernate.event.DeleteEvent
import org.hibernate.event.FlushEventListener
import org.hibernate.event.FlushEvent

class AuditEventListener extends SaveOrUpdateEventListener implements DeleteEventListener,
                                                                      PostCollectionUpdateEventListener,
                                                                      FlushEventListener {

    def elasticSearchContextHolder


    /**
     * Index & Delete requests are execute once per flush.
     * Before a flush event, the requests are store in callsBuffer and then executed once onFlush() is called.
     */
    IndexRequestBuffer callsBuffer = new IndexRequestBuffer()

    void onPostUpdateCollection(PostCollectionUpdateEvent postCollectionUpdateEvent) {
        def clazz = postCollectionUpdateEvent.affectedOwnerOrNull?.class
        // Todo : optimize reindexing requests
        if (elasticSearchContextHolder.getMappingContextByType(clazz)) {
            callsBuffer.addIndexRequest(postCollectionUpdateEvent.affectedOwnerOrNull)
        }
    }

    void onSaveOrUpdate(SaveOrUpdateEvent saveOrUpdateEvent) {
        def clazz = saveOrUpdateEvent.entity?.class
        if (elasticSearchContextHolder.getMappingContextByType(clazz)) {
            callsBuffer.addIndexRequest(saveOrUpdateEvent.entity)
        }
    }

    void onDelete(DeleteEvent deleteEvent) {
        def clazz = deleteEvent.object?.class
        if (elasticSearchContextHolder.getMappingContextByType(clazz)) {
            callsBuffer.addDeleteRequest(deleteEvent.object)
        }
    }

    void onDelete(DeleteEvent deleteEvent, Set set) {
        // TODO : for cascade delete
    }

    void onFlush(FlushEvent flushEvent) {
        // When a flush occurs, execute the pending requests in the buffer (the buffer is cleared automatically)
        callsBuffer.executeRequests()
    }

}

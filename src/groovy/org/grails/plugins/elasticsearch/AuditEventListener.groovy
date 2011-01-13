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
  /**
   * Index & Delete requests are execute once per flush.
   * Before a flush event, the requests are store in callsBuffer and then executed once onFlush() is called.
   */
  IndexRequestBuffer callsBuffer = new IndexRequestBuffer()

  void onPostUpdateCollection(PostCollectionUpdateEvent postCollectionUpdateEvent) {
    def clazz = postCollectionUpdateEvent.affectedOwnerOrNull?.class
    // Todo : optimize reindexing requests
    if (clazz?.searchable) {
      callsBuffer.addIndexRequest(postCollectionUpdateEvent.affectedOwnerOrNull)
    }
  }

  void onSaveOrUpdate(SaveOrUpdateEvent saveOrUpdateEvent) {
    def clazz = saveOrUpdateEvent.entity?.class
    if (clazz?.searchable) {
      callsBuffer.addIndexRequest(saveOrUpdateEvent.entity)
    }
  }

  void onDelete(DeleteEvent deleteEvent) {
    def clazz = deleteEvent.object?.class
    if (clazz?.searchable) {
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

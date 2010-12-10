package org.grails.plugins.elasticsearch

import org.hibernate.event.PostInsertEventListener
import org.hibernate.event.PostUpdateEventListener
import org.hibernate.event.PostInsertEvent
import org.hibernate.event.PostUpdateEvent
import org.hibernate.event.PreDeleteEventListener
import org.hibernate.event.PreDeleteEvent
import org.grails.plugins.elasticsearch.util.ElasticSearchUtils

class AuditEventListener implements PostInsertEventListener, PostUpdateEventListener, PreDeleteEventListener {

  void onPostInsert(PostInsertEvent postInsertEvent) {
    def clazz = postInsertEvent.entity?.class
    if (clazz?.searchable) {
      ElasticSearchUtils.indexDomain(postInsertEvent.entity)
    }
  }

  void onPostUpdate(PostUpdateEvent postUpdateEvent) {
    def clazz = postUpdateEvent.entity?.class
    if (clazz?.searchable) {
      ElasticSearchUtils.indexDomain(postUpdateEvent.entity)
    }
  }

  boolean onPreDelete(PreDeleteEvent preDeleteEvent) {
    // todo
    return true
  }
}

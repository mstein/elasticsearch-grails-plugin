package org.grails.plugins.elasticsearch

import org.hibernate.EmptyInterceptor
import org.hibernate.type.Type

/**
 * Indexes a domain class everytime a domain instance is saved
 *
 * @author Graeme Rocher
 *
 */
class ElasticSearchInterceptor extends EmptyInterceptor{

  org.grails.plugins.elasticsearch.ElasticSearchIndexService elasticSearchIndexService

  boolean onSave(Object entity, Serializable id, Object[] state, String[] propertyNames, Type[] types) {
    def clazz = entity?.class
    if(clazz?.searchable) {
      elasticSearchIndexService.indexDomain(entity) 
    }
    return false
  }

  void onDelete(Object entity, Serializable id, Object[] state, String[] propertyNames, Type[] types) {
    def clazz = entity?.class
    if(clazz?.searchable) {
      elasticSearchIndexService.deleteDomain(entity)
    }
    super.onDelete(entity, id, state, propertyNames, types);    
  }


}
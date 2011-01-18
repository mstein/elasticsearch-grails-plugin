package org.grails.plugins.elasticsearch

import org.codehaus.groovy.grails.commons.GrailsDomainClass
import org.grails.plugins.elasticsearch.mapping.SearchableClassMapping

class ElasticSearchContextHolder {
  ConfigObject config
  Map<String, SearchableClassMapping> mapping = [:]

  public void addMappingContext(SearchableClassMapping scm){
    mapping[scm.domainClass.fullName] = scm
  }

  SearchableClassMapping getMappingContext(String type) {
    mapping[type]
  }

  SearchableClassMapping getMappingContext(GrailsDomainClass domainClass) {
    mapping[domainClass.fullName]
  }

  SearchableClassMapping getMappingContextByType(Class clazz) {
    mapping.values().find { scm -> scm.domainClass.clazz == clazz }
  }
}

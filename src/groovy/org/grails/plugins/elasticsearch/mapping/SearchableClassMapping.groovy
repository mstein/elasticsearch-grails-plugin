package org.grails.plugins.elasticsearch.mapping

import org.codehaus.groovy.grails.commons.GrailsDomainClass

class SearchableClassMapping {
  Collection<SearchableClassPropertyMapping> propertiesMapping
  GrailsDomainClass domainClass
  Map classMapping

  public SearchableClassPropertyMapping getPropertyMapping(propertyName){
    propertiesMapping.find {
      it.propertyName == propertyName
    }
  }
}

package org.grails.plugins.elasticsearch

import org.codehaus.groovy.grails.commons.GrailsDomainClass

class ElasticSearchContextHolder {
  def config
  def mapping = [:]

  public void addMappingContext(GrailsDomainClass domainClass, mappedProperties){
    mapping[domainClass.propertyName] = mappedProperties
  }

  public void addMappingContext(String type, mappedProperties){
    mapping[type] = mappedProperties
  }

  def getMappingContext(String type) {
    mapping[type]
  }

  def getMappingContext(GrailsDomainClass domainClass) {
    mapping[domainClass.propertyName]
  }
}

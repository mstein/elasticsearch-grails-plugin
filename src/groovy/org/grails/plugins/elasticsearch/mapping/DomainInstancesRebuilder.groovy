package org.grails.plugins.elasticsearch.mapping

import org.codehaus.groovy.grails.commons.GrailsDomainClass
import org.codehaus.groovy.grails.web.metaclass.BindDynamicMethod
import org.springframework.beans.SimpleTypeConverter
import org.elasticsearch.search.SearchHit

class DomainInstancesRebuilder {
  def elasticSearchContextHolder
  def grailsApplication

  public Collection buildResults(GrailsDomainClass domainClass, hits) {
    def typeConverter = new SimpleTypeConverter()
    BindDynamicMethod bind = new BindDynamicMethod()
    def mapContext = elasticSearchContextHolder.getMappingContext(domainClass.propertyName)

    hits.collect { SearchHit hit ->
      def identifier = domainClass.getIdentifier()
      def id = typeConverter.convertIfNecessary(hit.id(), identifier.getType())
      def instance = domainClass.newInstance()
      instance."${identifier.name}" = id

      def rebuiltProperties = [:]
      hit.source.each { name, value ->
        rebuiltProperties += [(name):buildProperty(value)]
      }
      def args = [instance, rebuiltProperties] as Object[]
      bind.invoke(instance, 'bind', args)

      return instance
    }
  }

  private Boolean isDomain(Map data) {
    if (data instanceof Map && data.'class') {
      return grailsApplication.domainClasses.any { it.clazz.name == data.'class' }
    }
    return false
  }

  private Boolean isDomainCollection(Map data) {

  }

  private Object buildProperty(propertyValue) {
    // TODO : adapt behavior if the mapping option "component" or "reference" are set
      // below is considering the "component" behavior
    if (propertyValue instanceof Map) {
      if (isDomain(propertyValue)) {
        return buildDomain(propertyValue)
      } else {
        return propertyValue
      }
    } else if (propertyValue instanceof Collection) {
      return propertyValue.collect {
        buildProperty(it)
      }
    } else {
      return propertyValue
    }
  }

  private Object buildDomain(data) {
    def domainClass = grailsApplication.domainClasses.find { it.clazz.name == data.'class' }
    def typeConverter = new SimpleTypeConverter()

    if (domainClass) {
      def identifier = domainClass.getIdentifier()
      def id = typeConverter.convertIfNecessary(data.id, identifier.getType())
      def instance = domainClass.newInstance()
      instance."${identifier.name}" = id

      instance.properties = data
      return instance
    }
    return null
  }
}

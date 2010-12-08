package org.grails.plugins.elasticsearch.conversion

import org.codehaus.groovy.grails.commons.GrailsDomainClass
import org.codehaus.groovy.grails.web.metaclass.BindDynamicMethod
import org.springframework.beans.SimpleTypeConverter
import org.elasticsearch.search.SearchHit
import org.grails.plugins.elasticsearch.conversion.unmarshall.DefaultUnmarshallingContext
import org.grails.plugins.elasticsearch.conversion.unmarshall.CycleReferenceSource

class DomainInstancesRebuilder {
  def elasticSearchContextHolder
  def grailsApplication

  public Collection buildResults(GrailsDomainClass domainClass, hits) {
    def typeConverter = new SimpleTypeConverter()
    BindDynamicMethod bind = new BindDynamicMethod()
    def mapContext = elasticSearchContextHolder.getMappingContext(domainClass.propertyName)
    def unmarshallingContext = new DefaultUnmarshallingContext()

    hits.collect { SearchHit hit ->
      def identifier = domainClass.getIdentifier()
      def id = typeConverter.convertIfNecessary(hit.id(), identifier.getType())
      def instance = domainClass.newInstance()
      instance."${identifier.name}" = id

      def rebuiltProperties = [:]
      hit.source.each { name, value ->
        unmarshallingContext.unmarshallingStack.push(name)
        rebuiltProperties += [(name):buildProperty(value, unmarshallingContext)]
        populateCyclicReference(instance, rebuiltProperties, unmarshallingContext)
        unmarshallingContext.resetContext()
      }
      def args = [instance, rebuiltProperties] as Object[]
      bind.invoke(instance, 'bind', args)

      return instance
    }
  }

  private populateCyclicReference(instance, rebuiltProperties, unmarshallingContext) {
    unmarshallingContext.cycleRefStack.each { CycleReferenceSource cr ->
      populateProperty(cr.cyclePath, rebuiltProperties, resolvePath(cr.sourcePath, instance, rebuiltProperties))
    }
  }

  private resolvePath(path, instance, rebuiltProperties) {
    if(!path) {
      return instance
    } else {
      def splitted = path.split('/').toList()
      def currentProperty = rebuiltProperties
      splitted.each {
        if(it.isNumber())
          currentProperty = currentProperty.asList()[it.toInteger()]
        else
          currentProperty = currentProperty[it]
      }
      return currentProperty
    }
  }

  private populateProperty(path, rebuiltProperties, value){
    def splitted = path.split('/').toList()
    def last = splitted.last()
    def currentProperty = rebuiltProperties
    def size = splitted.size()
    splitted.eachWithIndex { it, index ->
      if (index < size-1) {
        try {
          if (currentProperty instanceof Collection) {
            currentProperty = currentProperty.asList()[it.toInteger()]
          } else {
            currentProperty = currentProperty.getAt(it)
          }

        } catch (Exception e) {
          println "/!\\ Error when trying to populate ${path}"
          println "Cannot get ${it} on ${currentProperty} from ${rebuiltProperties}"
          e.printStackTrace()
        }
      }
    }
    if(last.isNumber()){
      currentProperty << value
    } else {
      currentProperty."${last}" = value
    }

  }

  private Boolean isDomain(Map data) {
    if (data instanceof Map && data.'class') {
      return grailsApplication.domainClasses.any { it.clazz.name == data.'class' }
    }
    return false
  }

  private Object buildProperty(propertyValue, unmarshallingContext) {
    // TODO : adapt behavior if the mapping option "component" or "reference" are set
    // below is considering the "component" behavior
    def parseResult = propertyValue
    if (propertyValue instanceof Map) {
      // Handle cycle reference
      if(propertyValue.ref) {
        unmarshallingContext.addCycleRef(propertyValue)
        return null
      }
      if (isDomain(propertyValue)) {
        parseResult = buildDomain(propertyValue, unmarshallingContext)
      } else {
        parseResult = propertyValue
      }
    } else if (propertyValue instanceof Collection) {
      parseResult = []
      propertyValue.eachWithIndex { value, index ->
        unmarshallingContext.unmarshallingStack.push(index)
        def built = buildProperty(value, unmarshallingContext)
        if(built)
          parseResult << built
        unmarshallingContext.unmarshallingStack.pop()
      }
    }
    return parseResult
  }

  private Object buildDomain(data, unmarshallingContext) {
    def domainClass = grailsApplication.domainClasses.find { it.clazz.name == data.'class' }
    def typeConverter = new SimpleTypeConverter()

    if (domainClass) {
      def identifier = domainClass.getIdentifier()
      def id = typeConverter.convertIfNecessary(data.id, identifier.getType())
      def instance = domainClass.newInstance()
      instance."${identifier.name}" = id
      BindDynamicMethod bind = new BindDynamicMethod()

      data.each { key, value ->
        if (key != 'class' && key != 'id') {
          unmarshallingContext.unmarshallingStack.push(key)
          def args = [instance, [(key):buildProperty(value, unmarshallingContext)]] as Object[]
          bind.invoke(instance, 'bind', args)
          unmarshallingContext.unmarshallingStack.pop()
        }
      }
      return instance
    }
    return null
  }
}

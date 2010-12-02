package org.grails.plugins.elasticsearch.mapping

import org.codehaus.groovy.grails.commons.GrailsDomainClass
import org.codehaus.groovy.grails.commons.GrailsDomainClassProperty

class ClosureSearchableDomainClassMapper {
  def CLASS_MAPPING_OPTIONS = ['all']
  def SEARCHABLE_MAPPING_OPTIONS = ['boost', 'component']

  Map mappingOptionsValues
  def mappableProperties
  def mappedProperties = []
  def mappedClass
  GrailsDomainClass grailsDomainClass
  def searchableValues
  def onlyMode = false

  ClosureSearchableDomainClassMapper(GrailsDomainClass domainClass){
    mappableProperties = domainClass.getProperties()*.name
  }

  def getPropertyMappings(GrailsDomainClass domainClass, Collection searchableDomainClasses, Boolean searchableValues) {
    assert searchableValues instanceof Boolean
    assert searchableValues

    grailsDomainClass = domainClass
    grailsDomainClass.getProperties().each { property ->
      mappedProperties << this.getDefaultMapping(property)
    }

    return mappedProperties
  }

  def getPropertyMappings(GrailsDomainClass domainClass, Collection searchableDomainClasses, Object searchableValues) {
    assert searchableValues instanceof Closure

    grailsDomainClass = domainClass

    // Build user-defined specific mappings
    def closure = (Closure) searchableValues.clone()
    closure.setDelegate(this)
    closure.call()

    // TODO : if 'only' is not set, add default mapping to not explicitly set properties
    return mappedProperties
  }

  Object invokeMethod(String name, Object args) {
    // Special cases (only, except, ...)
    if (name.equals('only')) {
      onlyMode = true
      mappableProperties = args
      return
    }

    // Predefined mapping options
    if (CLASS_MAPPING_OPTIONS.contains(name)) {
      if (!args) {
        throw new IllegalArgumentException("${grailsDomainClass.propertyName} mapping declares ${name} : found no argument.")
      }
      mappingOptionsValues[name] = args[0]
      return
    }

    // Custom properties mapping options
    def property = grailsDomainClass.getProperties().find { it.name == name }
    if (!property) {
      throw new IllegalArgumentException("Unable to find property [${name}] used in [${grailsDomainClass.propertyName}#searchable].")
    }
    if (!mappableProperties.any { it == property.name }) {
      if (onlyMode) {
        throw new IllegalArgumentException("""Unable to map [${grailsDomainClass.propertyName}.${property.name}].
          You can only customize the mapping of the properties you listed in the only field.""")
      } else {
        throw new IllegalArgumentException("Unable to map [${grailsDomainClass.propertyName}.${property.name}].")
      }
    }

    def defaultMapping = this.getDefaultMapping(property)
    this.validateOptions(args[0])
    defaultMapping.attributes += args[0]
    mappedProperties << defaultMapping
  }

  private SearchableClassPropertyMapping getDefaultMapping(property){
    new SearchableClassPropertyMapping(propertyName: property.name, propertyType: property.type)
  }

  private void validateOptions(propertyMapping) {
    def invalidOptions = propertyMapping.keySet() - SEARCHABLE_MAPPING_OPTIONS
    if(invalidOptions){
      throw new IllegalArgumentException("Invalid options found in searchable mapping ${invalidOptions}.")
    }
  }
}

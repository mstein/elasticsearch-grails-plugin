package org.grails.plugins.elasticsearch.mapping

import org.codehaus.groovy.grails.commons.GrailsDomainClass

class ClosureSearchableDomainClassMapper {
  static final CLASS_MAPPING_OPTIONS = ['all', 'root']
  static final SEARCHABLE_MAPPING_OPTIONS = ['boost', 'index']
  static final SEARCHABLE_SPECIAL_MAPPING_OPTIONS = ['component']

  def elasticSearcConfig

  def classMapping
  def mappableProperties
  def mappedProperties = []
  def customMappedProperties = []
  def mappedClass
  GrailsDomainClass grailsDomainClass
  def searchableValues
  def only
  def except

  ClosureSearchableDomainClassMapper(GrailsDomainClass domainClass, elasticSearcConfig) {
    this.elasticSearcConfig = elasticSearcConfig
    mappableProperties = domainClass.getProperties()*.name
    grailsDomainClass = domainClass
  }

  public SearchableClassMapping getClassMapping(GrailsDomainClass domainClass, Collection searchableDomainClasses, Boolean searchableValues) {
    assert searchableValues instanceof Boolean
    assert searchableValues

    grailsDomainClass = domainClass
    grailsDomainClass.properties.each { property ->
      if(elasticSearcConfig.defaultExcludedProperties && !(property.name in elasticSearcConfig.defaultExcludedProperties))
        mappedProperties << this.getMapping(property)
    }
    classMapping = ['root':true]
    return new SearchableClassMapping(propertiesMapping: mappedProperties, classMapping: classMapping, domainClass: domainClass)
  }

  public SearchableClassMapping getClassMapping(GrailsDomainClass domainClass, Collection searchableDomainClasses, Object searchableValues) {
    assert searchableValues instanceof Closure

    grailsDomainClass = domainClass
    classMapping = ['root':true]

    // Build user-defined specific mappings
    def closure = (Closure) searchableValues.clone()
    closure.setDelegate(this)
    closure.call()

    if (only && except) throw new IllegalArgumentException("Both 'only' and 'except' were used in '${grailsDomainClass.propertyName}#searchable': provide one or neither but not both")
    if (except) {
      if (except instanceof String) {
        mappableProperties.remove(except)
      } else if (except instanceof Collection) {
        mappableProperties.removeAll(except)
      }
    }
    if (only) {
      if (only instanceof String) {
        mappableProperties = [only]
      } else if (only instanceof Collection) {
        mappableProperties = only
      }
    }
    if (customMappedProperties) {
      customMappedProperties.removeAll {
        !(it.propertyName in mappableProperties)
      }
    }
    mappedProperties.removeAll {
      !(it.propertyName in mappableProperties)
    }
    mappedProperties = (mappedProperties ?: []) + customMappedProperties
    def defaultMappableProperties = mappableProperties - mappedProperties*.propertyName
    defaultMappableProperties.each {
      mappedProperties << this.getMapping(grailsDomainClass.getPropertyByName(it))
    }
    return new SearchableClassMapping(propertiesMapping: mappedProperties, classMapping: classMapping, domainClass: domainClass)
  }

  Object invokeMethod(String name, Object args) {
    // Predefined mapping options
    if (CLASS_MAPPING_OPTIONS.contains(name)) {
      if (!args) {
        throw new IllegalArgumentException("${grailsDomainClass.propertyName} mapping declares ${name} : found no argument.")
      }
      classMapping[name] = args[0]
      return
    }

    // Custom properties mapping options
    def property = grailsDomainClass.getProperties().find { it.name == name }
    if (!property) {
      throw new IllegalArgumentException("Unable to find property [${name}] used in [${grailsDomainClass.propertyName}#searchable].")
    }
    if (!mappableProperties.any { it == property.name }) {
      throw new IllegalArgumentException("Unable to map [${grailsDomainClass.propertyName}.${property.name}].")
    }

    def defaultMapping = this.getMapping(property)
    this.validateOptions(args[0])
    defaultMapping.addAttributes(args[0])
    customMappedProperties << defaultMapping
  }

  private SearchableClassPropertyMapping getMapping(property) {
    if(customMappedProperties){
      SearchableClassPropertyMapping existingMapping = customMappedProperties.find { it.propertyName == property.name }
      if(existingMapping){
        return existingMapping
      }
    }
    return new SearchableClassPropertyMapping(property)
  }

  private void validateOptions(propertyMapping) {
    def invalidOptions = propertyMapping.keySet() - (SEARCHABLE_MAPPING_OPTIONS + SEARCHABLE_SPECIAL_MAPPING_OPTIONS)
    if (invalidOptions) {
      throw new IllegalArgumentException("Invalid options found in searchable mapping ${invalidOptions}.")
    }
  }
}

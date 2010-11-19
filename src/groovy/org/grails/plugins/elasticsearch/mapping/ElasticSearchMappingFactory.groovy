package org.grails.plugins.elasticsearch.mapping

import org.codehaus.groovy.grails.commons.GrailsDomainClass
import grails.converters.JSON
import grails.web.JSONBuilder
import org.codehaus.groovy.grails.commons.DefaultGrailsDomainClass
import org.codehaus.groovy.grails.commons.DefaultGrailsDomainClassProperty

class ElasticSearchMappingFactory {
  static SUPPORTED_FORMAT = ['string', 'integer', 'long', 'float', 'double', 'boolean', 'null']
  static JSON getElasticMapping(GrailsDomainClass domainClass, Collection<SearchableClassPropertyMapping> propertyMappings) {
    def properties = domainClass.getProperties()
    def mapBuilder = [
            (domainClass.propertyName):[
                    properties:[:]
            ]
    ]
    properties.each {DefaultGrailsDomainClassProperty prop ->
      def propType = prop.typePropertyName
      def propOptions = [:]
      if(!(prop.typePropertyName in SUPPORTED_FORMAT)){
        propType = 'object'
      }
      propOptions.type = propType
      def customMapping = propertyMappings.find {it.propertyName == prop.name}
      if(customMapping) {
        customMapping.attributes.each { key, value ->
          propOptions."${key}" = value
        }
      }
      mapBuilder."${domainClass.propertyName}".properties << ["${prop.name}":propOptions]
    }

    return mapBuilder as JSON
  }
}

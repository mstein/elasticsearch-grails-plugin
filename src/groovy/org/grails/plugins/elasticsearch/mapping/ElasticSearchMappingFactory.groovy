package org.grails.plugins.elasticsearch.mapping

import org.codehaus.groovy.grails.commons.GrailsDomainClass
import grails.converters.JSON

class ElasticSearchMappingFactory {
  static  getElasticMapping(GrailsDomainClass domainClass, Collection<SearchableClassPropertyMapping> propertyMappings) {
    def properties = domainClass.getProperties()
    def builders = {
      "${domainClass.propertyName}" {
        "properties" {
          properties.each { prop ->
            // TODO : consider the propertyMappings value (boost, ...)
            "${prop.name}" {
              "type"('object')
              element(name:'id', type:'long')
              // TODO : deep mapping ?
            }
          }
        }
      }
    }

    return builders
  }
}

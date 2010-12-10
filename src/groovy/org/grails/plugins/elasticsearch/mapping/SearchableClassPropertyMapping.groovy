package org.grails.plugins.elasticsearch.mapping

import org.codehaus.groovy.grails.commons.GrailsDomainClassProperty

class SearchableClassPropertyMapping {
  /** The name of the class property */
  String propertyName
  /** The type of the class property */
  Class propertyType
  /** Mapping attributes values, will be added in the ElasticSearch JSON mapping request */
  Map attributes = [:]
  /** Special mapping attributes, only used by the plugin itself (eg: 'component', 'root') */
  Map specialAttributes = [:]

  public SearchableClassPropertyMapping(GrailsDomainClassProperty property){
    this.propertyName = property.name
    this.propertyType = property.type
  }

  public SearchableClassPropertyMapping(GrailsDomainClassProperty property, Map options){
    this.propertyName = property.name
    this.propertyType = property.type
    this.addAttributes(options)
  }

  public addAttributes(attributesMap){
    attributesMap.each { key, value ->
      if(key in ClosureSearchableDomainClassMapper.SEARCHABLE_MAPPING_OPTIONS) {
        attributes += [(key):value]
      } else if (key in ClosureSearchableDomainClassMapper.SEARCHABLE_SPECIAL_MAPPING_OPTIONS) {
        specialAttributes += [(key):value]
      }
    }
  }

  public Boolean isComponent(){
    specialAttributes.any { k, v -> k == 'component' && v }
  }
}

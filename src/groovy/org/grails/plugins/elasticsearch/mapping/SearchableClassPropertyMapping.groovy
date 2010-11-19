package org.grails.plugins.elasticsearch.mapping

class SearchableClassPropertyMapping {
  /** The name of the class property */
  String propertyName
  /** The type of the class property */
  Class propertyType
  /** Mapping attributes values */
  Map attributes = [:]
}

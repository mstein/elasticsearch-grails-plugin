package org.grails.plugins.elasticsearch

import org.codehaus.groovy.grails.commons.GrailsDomainClassProperty
import org.elasticsearch.common.xcontent.XContentBuilder
import static org.elasticsearch.common.xcontent.XContentFactory.*
import org.codehaus.groovy.grails.commons.GrailsDomainClass
import org.springframework.context.ApplicationContext
import org.codehaus.groovy.grails.commons.ApplicationHolder
import org.grails.plugins.elasticsearch.conversion.marshall.DeepDomainClassMarshaller
import org.grails.plugins.elasticsearch.conversion.marshall.DefaultMarshallingContext
import org.grails.plugins.elasticsearch.conversion.marshall.DefaultMarshaller
import org.grails.plugins.elasticsearch.conversion.marshall.MapMarshaller
import org.grails.plugins.elasticsearch.conversion.marshall.CollectionMarshaller
import org.codehaus.groovy.grails.commons.DomainClassArtefactHandler

class JSONDomainFactory {
  /**
   * The default marshallers, not defined by user
   */
  def static DEFAULT_MARSHALLERS = [
          (Map):MapMarshaller,
          (Collection):CollectionMarshaller
  ]

  /**
   * Create and use the correct marshaller for a peculiar class
   * @param object The instance to marshall
   * @param marshallingContext The marshalling context associate with the current marshalling process
   * @return Object The result of the marshall operation.
   */
  public delegateMarshalling(object, marshallingContext) {
    if(object == null) {
      return null
    }
    def marshaller
    def objectClass = object.class

    // TODO : support user custom marshaller (& marshaller registration)
    // Check for direct marshaller matching
    if(DEFAULT_MARSHALLERS[objectClass]) {
      marshaller = DEFAULT_MARSHALLERS[objectClass].newInstance()
      marshaller.marshallingContext = marshallingContext
    // Check for domain classes
    } else if(DomainClassArtefactHandler.isDomainClass(objectClass)) {
      marshaller = new DeepDomainClassMarshaller(marshallingContext:marshallingContext)
    } else {
      // Check for inherited marshaller matching
      def inheritedMarshaller = DEFAULT_MARSHALLERS.find { key, value -> key.isAssignableFrom(objectClass)}
      if(inheritedMarshaller) {
        marshaller = DEFAULT_MARSHALLERS[inheritedMarshaller.key].newInstance()
        marshaller.marshallingContext = marshallingContext
      // If no marshaller was found, use the default one
      } else {
        marshaller = new DefaultMarshaller(marshallingContext:marshallingContext)
      }
    }
    marshaller.marshall(object)
  }

  private static isDomainClass(String className) {
    def grailsApplication = ApplicationHolder.application
    return className.toLowerCase() in grailsApplication.domainClasses*.naturalName*.toLowerCase()
  }

  private static isDomainClass(instance) {
    def grailsApplication = ApplicationHolder.application
    return instance.class?.simpleName in grailsApplication.domainClasses*.naturalName
  }

  private static GrailsDomainClass getDomainClass(instance) {
    def grailsApplication = ApplicationHolder.application
    grailsApplication.domainClasses.find {it.naturalName == instance.class?.simpleName}
  }

  private static marshallCollection(collection, maxDepth) {
    if (maxDepth > 0) {
      return collection.collect {
        if (it instanceof Collection) {
          marshallCollection(it, maxDepth - 1)
        } else if (it instanceof Map) {
          marshallMap(it, maxDepth - 1)
        } else if (isDomainClass(it)) {
          deepMarshallDomain(it, maxDepth - 1)
        }
      }
    } else {
      []
    }
  }

  private static marshallMap(map, maxDepth) {
    def marshallResult = [:]
    if (maxDepth > 0) {
      map.each { key, value ->
        if (value instanceof Collection) {
          marshallResult."${key}" = marshallCollection(value, maxDepth - 1)
        } else if (value instanceof Map) {
          marshallResult."${key}" = marshallMap(value, maxDepth - 1)
        } else if (isDomainClass(value)) {
          marshallResult."${key}" = deepMarshallDomain(value, maxDepth - 1)
        }
      }
    }
    return marshallResult
  }

  private static deepMarshallDomain(instance, maxDepth) {
    def grailsApplication = ApplicationHolder.application
    def marshallResult = [id: instance.id, 'class': instance.class?.simpleName]
    if (maxDepth > 0) {
      def domainClass = getDomainClass(instance)
      for (GrailsDomainClassProperty prop in domainClass.persistantProperties) {
        def propertyClassName = instance."${prop.name}"?.class?.simpleName
        def propertyValue = instance."${prop.name}"

        // Collection marshalling
        if (propertyValue instanceof Collection) {
          marshallResult += [(prop.name): marshallCollection(propertyValue, maxDepth - 1)]

          // Map marshalling
        } else if (propertyValue instanceof Map) {
          marshallResult += [(prop.name): marshallMap(propertyValue, maxDepth - 1)]

          // Domain marshalling
        } else if (propertyClassName in grailsApplication.domainClasses*.naturalName) {
          if (propertyValue.class.searchable) {
            marshallResult += [(prop.name): ([id: instance.id] + deepMarshallDomain(propertyValue, maxDepth - 1))]
          } else {
            marshallResult += [(prop.name): [id: instance.id, 'class': propertyClassName]]
          }

          // Basic/unsupported types marshalling
        } else {
          marshallResult += [(prop.name): propertyValue]
        }
      }
    }
    return marshallResult
  }

  /**
   * Build an XContentBuilder representing a domain instance in JSON.
   * Use as a source to an index request to ElasticSearch.
   * @param instance A domain class instance.
   * @return
   */
  public XContentBuilder buildJSON2(instance) {
    println 'buildJSON2 called!'
    def domainClass = getDomainClass(instance)
    def json = jsonBuilder().startObject()
    // TODO : add maxDepth in custom mapping (only for "seachable components")
    // TODO : detect cyclic association
    def marshallingContext = new DefaultMarshallingContext(maxDepth:5, parentFactory:this)
    marshallingContext.marshallStack.push(instance)
    // Build the json-formated map that will contain the data to index
    for (GrailsDomainClassProperty prop in domainClass.persistantProperties) {
      def res = this.delegateMarshalling(instance."${prop.name}", marshallingContext)
      json.field(prop.name, res)
    }
    marshallingContext.marshallStack.pop()
    json.endObject()
  }

  public static XContentBuilder buildJSON(domainClass, instance) {
    def grailsApplication = ApplicationHolder.application
    def json = jsonBuilder().startObject()
    // TODO : add maxDepth in custom mapping (only for "seachable components")
    // TODO : detect cyclic association
    def maxDepth = 5
    // Build the json-formated map that will contain the data to index
    for (GrailsDomainClassProperty prop in domainClass.persistantProperties) {
      // Associations with other domain classes are indexed only if those are searchable as well
      def propertyClassName = instance."${prop.name}"?.class?.simpleName
      def propertyValue = instance."${prop.name}"

      // Collection marshalling
      if (propertyValue instanceof Collection) {
        json.field(prop.name, marshallCollection(propertyValue, maxDepth - 1))

        // Map marshalling
      } else if (propertyValue instanceof Map) {
        json.field(prop.name, marshallMap(propertyValue, maxDepth - 1))

        // Domain marshalling
      } else if (propertyClassName in grailsApplication.domainClasses*.naturalName) {
        if (propertyValue.class.searchable) {
          json.field(prop.name, deepMarshallDomain(propertyValue, maxDepth - 1))
        } else {
          json.field(prop.name, [id: instance.id, 'class': propertyClassName])
        }

        // Basic/unsupported types marshalling
      } else {
        json.field(prop.name, propertyValue)
      }
    }
    json.endObject()
  }
}

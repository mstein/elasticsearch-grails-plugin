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

  private static GrailsDomainClass getDomainClass(instance) {
    def grailsApplication = ApplicationHolder.application
    grailsApplication.domainClasses.find {it.naturalName == instance.class?.simpleName}
  }

  /**
   * Build an XContentBuilder representing a domain instance in JSON.
   * Use as a source to an index request to ElasticSearch.
   * @param instance A domain class instance.
   * @return
   */
  public XContentBuilder buildJSON(instance) {
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
}

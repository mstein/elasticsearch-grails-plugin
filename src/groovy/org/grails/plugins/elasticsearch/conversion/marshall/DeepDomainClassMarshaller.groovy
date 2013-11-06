package org.grails.plugins.elasticsearch.conversion.marshall

import org.codehaus.groovy.grails.commons.ApplicationHolder
import org.codehaus.groovy.grails.commons.DomainClassArtefactHandler
import org.codehaus.groovy.grails.commons.GrailsDomainClass
import org.codehaus.groovy.grails.commons.GrailsDomainClassProperty
import org.codehaus.groovy.grails.orm.hibernate.cfg.GrailsHibernateUtil

class DeepDomainClassMarshaller extends DefaultMarshaller {
  protected doMarshall(instance) {
    def domainClass = getDomainClass(instance)
    // don't use instance class directly, instead unwrap from javaassist
    def marshallResult = [id: instance.id, 'class': domainClass.clazz.name]
    def scm = elasticSearchContextHolder.getMappingContext(domainClass)
    if (!scm) {
        throw new IllegalStateException("Domain class ${domainClass} is not searchable.")
    }
    for (GrailsDomainClassProperty prop in domainClass.persistantProperties) {
      def propertyMapping = scm.getPropertyMapping(prop.name)
      if (!propertyMapping) {
        continue
      }
      def propertyClassName = instance."${prop.name}"?.class?.name
      def propertyClass = instance."${prop.name}"?.class
      def propertyValue = instance."${prop.name}"

      // Domain marshalling
      if (DomainClassArtefactHandler.isDomainClass(propertyClass)) {
        if (propertyValue.class.searchable) {   // todo fixme - will throw exception when no searchable field.
          marshallingContext.lastParentPropertyName = prop.name
          marshallResult += [(prop.name): ([id: propertyValue.ident(), 'class': propertyClassName] + marshallingContext.delegateMarshalling(propertyValue, propertyMapping.maxDepth))]
        } else {
          marshallResult += [(prop.name): [id: propertyValue.ident(), 'class': propertyClassName]]
        }

        // Non-domain marshalling
      } else {
        marshallingContext.lastParentPropertyName = prop.name
        def marshalledValue = marshallingContext.delegateMarshalling(propertyValue)
        // Ugly XContentBuilder bug: it only checks for EXACT class match with java.util.Date
        // (sometimes it appears to be java.sql.Timestamp for persistent objects)
        if (marshalledValue instanceof java.util.Date) {
            marshalledValue = new java.util.Date(marshalledValue.getTime())
        }
        marshallResult += [(prop.name): marshalledValue]
      }
    }
    return marshallResult
  }

  protected nullValue(){
    return []
  }

  private GrailsDomainClass getDomainClass(instance) {
    def grailsApplication = ApplicationHolder.application
    def instanceClass = GrailsHibernateUtil.unwrapIfProxy(instance).class
    grailsApplication.domainClasses.find {it.clazz == instanceClass}
  }
}

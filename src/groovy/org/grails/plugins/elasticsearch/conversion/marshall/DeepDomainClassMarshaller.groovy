package org.grails.plugins.elasticsearch.conversion.marshall

import org.codehaus.groovy.grails.commons.GrailsDomainClassProperty
import org.codehaus.groovy.grails.commons.ApplicationHolder
import org.codehaus.groovy.grails.commons.GrailsDomainClass
import org.codehaus.groovy.grails.commons.DomainClassArtefactHandler

class DeepDomainClassMarshaller extends DefaultMarshaller {
  protected doMarshall(instance) {
    def marshallResult = [id: instance.id, 'class': instance.class?.name]
    def domainClass = getDomainClass(instance)
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
          marshallResult += [(prop.name): ([id: instance.ident(), 'class': propertyClassName] + marshallingContext.delegateMarshalling(instance, propertyMapping.maxDepth))]//this.marshall(propertyValue))]
        } else {
          marshallResult += [(prop.name): [id: instance.ident(), 'class': propertyClassName]]
        }

        // Non-domain marshalling
      } else {
        marshallingContext.lastParentPropertyName = prop.name
        marshallResult += [(prop.name): marshallingContext.delegateMarshalling(propertyValue)]
      }
    }
    return marshallResult
  }

  protected nullValue(){
    return []
  }

  private GrailsDomainClass getDomainClass(instance) {
    def grailsApplication = ApplicationHolder.application
    grailsApplication.domainClasses.find {it.clazz == instance.class}
  }
}

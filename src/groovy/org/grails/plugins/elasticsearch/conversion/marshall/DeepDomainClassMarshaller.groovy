package org.grails.plugins.elasticsearch.conversion.marshall

import org.codehaus.groovy.grails.commons.GrailsDomainClassProperty
import org.codehaus.groovy.grails.commons.ApplicationHolder
import org.codehaus.groovy.grails.commons.GrailsDomainClass

class DeepDomainClassMarshaller extends DefaultMarshaller {
  protected doMarshall(instance) {
    def grailsApplication = ApplicationHolder.application
    def marshallResult = [id: instance.id, 'class': instance.class?.simpleName]
    def domainClass = getDomainClass(instance)
    for (GrailsDomainClassProperty prop in domainClass.persistantProperties) {
      def propertyClassName = instance."${prop.name}"?.class?.simpleName
      def propertyValue = instance."${prop.name}"

      // Domain marshalling
      if (propertyClassName in grailsApplication.domainClasses*.naturalName) {
        if (propertyValue.class.searchable) {
          marshallResult += [(prop.name): ([id: instance.id, 'class':propertyClassName] + this.marshall(propertyValue))]
        } else {
          marshallResult += [(prop.name): [id: instance.id, 'class': propertyClassName]]
        }

        // Non-domain marshalling
      } else {
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
    grailsApplication.domainClasses.find {it.naturalName == instance.class?.simpleName}
  }
}

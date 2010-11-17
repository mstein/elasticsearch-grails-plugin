import grails.util.Environment
import org.codehaus.groovy.grails.commons.DefaultGrailsApplication
import grails.util.Metadata
import org.grails.plugins.elasticsearch.mapping.ClosureSearchableDomainClassMapper
import org.codehaus.groovy.grails.commons.GrailsDomainClass
import org.grails.plugins.elasticsearch.ElasticSearchInterceptor
import org.grails.plugins.elasticsearch.mapping.ElasticSearchMappingFactory
import grails.converters.JSON

class BootStrap {
  DefaultGrailsApplication grailsApplication

  def init = { servletContext ->
    // Define the custom ElasticSearch mapping for searchable domain classes
    grailsApplication.domainClasses.each { GrailsDomainClass domainClass ->
      if(domainClass.hasProperty('searchable') &&!(domainClass.getPropertyValue('searchable') instanceof Boolean && domainClass.getPropertyValue('searchable'))){
        def mappedProperties = (new ClosureSearchableDomainClassMapper(domainClass)).getPropertyMappings(domainClass, grailsApplication.domainClasses as List, domainClass.getPropertyValue('searchable'))
        def elasticMapping = ElasticSearchMappingFactory.getElasticMapping(domainClass, mappedProperties)
        println elasticMapping
      }
    }
  }

  def destroy = {

  }
}
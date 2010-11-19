package org.grails.plugins.elasticsearch

import grails.util.GrailsNameUtils
import grails.util.GrailsUtil
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.commons.GrailsDomainClass
import org.codehaus.groovy.grails.commons.GrailsDomainClassProperty
import org.codehaus.groovy.grails.plugins.support.aware.GrailsApplicationAware
import static org.elasticsearch.client.Requests.indexRequest
import static org.elasticsearch.common.xcontent.XContentFactory.*
import org.elasticsearch.groovy.client.GClient
import grails.converters.JSON

class ElasticSearchIndexService implements GrailsApplicationAware {

  GrailsApplication grailsApplication
  def elasticSearchHelper
  def sessionFactoryInstance

  boolean transactional = false

  void indexDomain(instance) {
    sessionFactoryInstance = grailsApplication.mainContext.getBean('sessionFactory')
    indexInBackground(instance, 0)
  }

  void deleteDomain(instance) {
    deleteInBackground(instance, 0)
  }

  private isDomainClass(String className) {
    return className.toLowerCase() in grailsApplication.domainClasses*.naturalName*.toLowerCase()
  }

  private isDomainClass(instance) {
    return instance.class?.simpleName in grailsApplication.domainClasses*.naturalName
  }

  private Thread deleteInBackground(instance, count) {
    return Thread.start {
      elasticSearchHelper.withElasticSearch { GClient client ->
        // TODO : delete in background

      }
    }
  }

  private Thread indexInBackground(instance, count) {
    return ThreadWithSession.startWithSession(sessionFactoryInstance) {
      try {
        elasticSearchHelper.withElasticSearch { GClient client ->
          Class clazz = instance.class
          GrailsDomainClass domainClass = grailsApplication.getDomainClass(clazz.name)
          String name = GrailsNameUtils.getPropertyName(clazz)
          def indexValue = clazz.getPackage().name ?: name

          def json = jsonBuilder().startObject()
          def json2
          JSON.use("deep") {
            println instance as JSON
            json2 = instance as JSON
          }
          // Build the json-formated map that will contain the data to index
          for (GrailsDomainClassProperty prop in domainClass.persistantProperties) {
            // If the property is a complex type (Domain, Collection, ...),
            // the JSON is formated to match the ES mapping convention
            // Associations with other domain classes are indexed only if those are searchable as well
            def propertyClassName = instance."${prop.name}"?.class?.simpleName
            def propertyValue = instance."${prop.name}"
            println "Check ${prop.name}..." + propertyClassName
            if (propertyClassName in grailsApplication.domainClasses*.naturalName) {
              if (propertyValue.class.searchable) {
                //json.field(prop.name, [type: instance."${prop.name}".class?.simpleName, id: instance."${prop.name}".id])
                println "Indexed ${prop.name} as JSON with value : " + (propertyValue.properties + [type: propertyClassName, id: instance."${prop.name}".id])
                json.field(prop.name, propertyValue.properties + [type: propertyClassName, id: instance."${prop.name}".id])
              }
            } else {
              json.field(prop.name, propertyValue)
              println "Indexed ${prop.name} string"
            }
          }
          json.endObject()
          client.index(
                  indexRequest(indexValue).type(name).id(instance.id.toString()).source(json2.toString())
          )
          /*client.index(
                  indexRequest(indexValue).type(name).id(instance.id.toString()).source(json)
          )*/
          println "Indexed domain type ${name} of id ${instance.id} and source ${json2}"
        }
      } catch (e) {
        e.printStackTrace()
        if (count < 10) {
          sleep 10000
          indexInBackground(instance, ++count)
        } else {
          GrailsUtil.deepSanitize(e)
          throw new IndexException("Failed to index domain instance [${instance}] after 10 retry attempts: ${e.message}", e)
        }
      }
    }
  }

}

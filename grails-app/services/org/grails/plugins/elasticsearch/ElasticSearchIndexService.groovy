package org.grails.plugins.elasticsearch

import grails.util.GrailsNameUtils
import grails.util.GrailsUtil
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.commons.GrailsDomainClass
import org.codehaus.groovy.grails.commons.GrailsDomainClassProperty
import org.codehaus.groovy.grails.plugins.support.aware.GrailsApplicationAware
import static org.elasticsearch.client.Requests.indexRequest
import static org.elasticsearch.common.xcontent.XContentFactory.*
import org.elasticsearch.client.Client
import org.elasticsearch.common.xcontent.XContentBuilder

class ElasticSearchIndexService implements GrailsApplicationAware {

  GrailsApplication grailsApplication
  def elasticSearchHelper
  def sessionFactoryInstance
  def jsonDomainFactory

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

  private GrailsDomainClass getDomainClass(instance) {
    grailsApplication.domainClasses.find {it.naturalName == instance.class?.simpleName}
  }

  private Thread deleteInBackground(instance, count) {
    return Thread.start {
      elasticSearchHelper.withElasticSearch { client ->
        // TODO : delete in background

      }
    }
  }

  private marshallCollection(collection, maxDepth){
    if(maxDepth > 0){
      return collection.collect {
        if(it instanceof Collection){
          marshallCollection(it, maxDepth - 1)
        } else if(it instanceof Map) {
          marshallMap(it, maxDepth - 1)
        } else if(isDomainClass(it)){
          deepMarshallDomain (it, maxDepth-1)
        }
      }
    } else {
      []
    }
  }

  private marshallMap(map, maxDepth){
    def marshallResult = [:]
    if(maxDepth > 0){
      map.each { key, value ->
        if(value instanceof Collection){
          marshallResult."${key}" = marshallCollection(value, maxDepth - 1)
        } else if(value instanceof Map) {
          marshallResult."${key}" = marshallMap(value, maxDepth - 1)
        } else if(isDomainClass(value)){
          marshallResult."${key}" = deepMarshallDomain (value, maxDepth-1)
        }
      }
    }
    return marshallResult
  }

  private deepMarshallDomain(instance, maxDepth) {
    def marshallResult = [id:instance.id, 'class':instance.class?.simpleName]
    if (maxDepth > 0) {
      def domainClass = getDomainClass(instance)
      for (GrailsDomainClassProperty prop in domainClass.persistantProperties) {
        def propertyClassName = instance."${prop.name}"?.class?.simpleName
        def propertyValue = instance."${prop.name}"

        // Collection marshalling
        if (propertyValue instanceof Collection) {
          marshallResult += [(prop.name):marshallCollection(propertyValue, maxDepth - 1)]

          // Map marshalling
        } else if (propertyValue instanceof Map) {
          marshallResult += [(prop.name):marshallMap(propertyValue, maxDepth - 1)]

          // Domain marshalling
        } else if (propertyClassName in grailsApplication.domainClasses*.naturalName) {
          if (propertyValue.class.searchable) {
            def dc = getDomainClass(propertyValue)
            marshallResult += [(prop.name):([id:instance.id] + deepMarshallDomain(propertyValue, maxDepth - 1))]
          } else {
            marshallResult += [(prop.name):[id:instance.id, 'class':propertyClassName]]
          }

          // Basic/unsupported types marshalling
        } else {
          marshallResult += [(prop.name):propertyValue]
        }
      }
    }
    return marshallResult
  }

  private XContentBuilder buildJSON(domainClass, instance) {
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
        json.field(prop.name, marshallCollection(propertyValue, maxDepth-1))

      // Map marshalling
      } else if(propertyValue instanceof Map) {
        json.field(prop.name, marshallMap(propertyValue, maxDepth-1))

      // Domain marshalling
      } else if (propertyClassName in grailsApplication.domainClasses*.naturalName) {
        if (propertyValue.class.searchable) {
          json.field(prop.name, deepMarshallDomain(propertyValue, maxDepth-1))
        } else {
          json.field(prop.name, [id:instance.id, 'class':propertyClassName])
        }

      // Basic/unsupported types marshalling
      } else {
        json.field(prop.name, propertyValue)
      }
    }

    json.endObject()
  }

  private Thread indexInBackground(instance, count) {
    return ThreadWithSession.startWithSession(sessionFactoryInstance) {
      try {
        elasticSearchHelper.withElasticSearch { Client client ->
          Class clazz = instance.class
          GrailsDomainClass domainClass = grailsApplication.getDomainClass(clazz.name)
          String name = GrailsNameUtils.getPropertyName(clazz)
          def indexValue = clazz.getPackage().name ?: name

          //def json = jsonBuilder().startObject()
          def json = jsonDomainFactory.buildJSON2(instance)
          /*def json2
          JSON.use("deep") {
            json2 = instance as JSON
            println json2.toString(true)
          }*/
          // Build the json-formated map that will contain the data to index
          /*for (GrailsDomainClassProperty prop in domainClass.persistantProperties) {
            // If the property is a complex type (Domain, Collection, ...),
            // the JSON is formated to match the ES mapping convention
            // Associations with other domain classes are indexed only if those are searchable as well
            def propertyClassName = instance."${prop.name}"?.class?.simpleName
            def propertyValue = instance."${prop.name}"
            if (propertyClassName in grailsApplication.domainClasses*.naturalName) {
              if (propertyValue.class.searchable) {
                println grailsApplication.domainClasses*.naturalName
                println propertyClassName
                def dc = grailsApplication.domainClasses.find {it.naturalName == propertyClassName}
                def propertiesMap = [id: instance."${prop.name}".id, type: propertyClassName]
                dc.persistantProperties.each { pn ->
                  propertiesMap += [(pn.name): propertyValue."${pn.name}"]
                }
                //json.field(prop.name, [type: instance."${prop.name}".class?.simpleName, id: instance."${prop.name}".id])
                //println "Indexed ${prop.name} as JSON with value : " + (propertyValue.properties + [type: propertyClassName, id: instance."${prop.name}".id])
                println "Indexed ${prop.name} as JSON with value : " + propertiesMap
                //json.field(prop.name, propertyValue.properties + [type: propertyClassName, id: instance."${prop.name}".id])
                json.field(prop.name, propertiesMap)
              }
            } else {
              json.field(prop.name, propertyValue)
            }
            // The class of the document (for object rebuilding)
            //json.field('class', prop.class)
          }
          json.endObject()*/
          /*client.index(
                  indexRequest(indexValue)
                          .type(name)
                          .id(instance.id.toString())
                          .source(json2.toString())
          )*/
          client.index(
                  indexRequest(indexValue)
                          .type(name)
                          .id(instance.id.toString())
                          .source(json)
          )
          /*println "Indexed domain type ${name} of id ${instance.id} and source ${json2.toString()}"*/
          println "Indexed domain type ${name} of id ${instance.id} and source ${json.string()}"
        }
      } catch (e) {
        e.printStackTrace()
        if (count < 5) {
          sleep 10000
          indexInBackground(instance, ++count)
        } else {
          GrailsUtil.deepSanitize(e)
          throw new IndexException("Failed to index domain instance [${instance}] after 5 retry attempts: ${e.message}", e)
        }
      }
    }
  }

}

package org.grails.plugins.elasticsearch

import grails.util.GrailsNameUtils
import grails.util.GrailsUtil
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.commons.GrailsDomainClass
import org.codehaus.groovy.grails.commons.GrailsDomainClassProperty
import org.codehaus.groovy.grails.plugins.support.aware.GrailsApplicationAware
import org.elasticsearch.client.Client
import static org.elasticsearch.client.Requests.indexRequest
import static org.elasticsearch.common.xcontent.XContentFactory.*
import org.elasticsearch.groovy.client.GClient
import static org.elasticsearch.client.Requests.indicesStatusRequest
import org.elasticsearch.action.get.GetResponse
import grails.converters.JSON
import org.elasticsearch.action.search.SearchType
import org.springframework.beans.SimpleTypeConverter
import org.elasticsearch.search.SearchHit
import static org.elasticsearch.search.builder.SearchSourceBuilder.*
import static org.elasticsearch.client.Requests.*
import static org.elasticsearch.index.query.xcontent.QueryBuilders.*
import static org.elasticsearch.client.Requests.searchRequest

class ElasticSearchIndexService implements GrailsApplicationAware {

  GrailsApplication grailsApplication
  def elasticSearchHelper

  boolean transactional = false

  void indexDomain(instance) {
    indexInBackground(instance, 0)
  }

  void search(String query){
    this.search(query, [:])
  }

  void search(String query, Map options) {
    elasticSearchHelper.withElasticSearch { GClient client ->
      try {
        def response = client.search(
                searchRequest()
                        .searchType(SearchType.DFS_QUERY_THEN_FETCH)
                        .source(searchSource().query(queryString(query))
                        .from(options.from ?: 0)
                        .size(options.size ?: 60)
                        .explain(options.containsKey('explain') ? options.explain : true))

        ).actionGet()
        def searchHits = response.hits()
        def result = [:]
        result.total = searchHits.totalHits()

        def typeConverter = new SimpleTypeConverter()
        result.searchResults = searchHits.hits().collect { SearchHit hit ->
          if(this.isDomainClass(hit.type)) {
            GrailsDomainClass domain = grailsApplication.domainClasses.find{it.naturalName.toLowerCase() == hit.type.toLowerCase()}
            def identifier = domain.getIdentifier()
            def id = typeConverter.convertIfNecessary(hit.id(), identifier.getType())
            def instance = domain.newInstance()
            instance."${identifier.name}" = id
            instance.properties = hit.source
            return instance
          }
        }
        return result
      } catch (e) {
        e.printStackTrace()
        return [searchResult: [], total: 0]
      }
    }
  }

  private isDomainClass(String className){
    return className.toLowerCase() in grailsApplication.domainClasses*.naturalName*.toLowerCase()
  }

  private isDomainClass(instance){
    return instance.class?.simpleName in grailsApplication.domainClasses*.naturalName
  }

  private Thread indexInBackground(instance, count) {
    return Thread.start {
      try {
        elasticSearchHelper.withElasticSearch { GClient client ->
          Class clazz = instance.class
          GrailsDomainClass domainClass = grailsApplication.getDomainClass(clazz.name)
          String name = GrailsNameUtils.getPropertyName(clazz)
          def indexValue = clazz.getPackage().name ?: name

          def json = jsonBuilder().startObject()
          // Build the json-formated map that will contain the data to index
          for (GrailsDomainClassProperty prop in domainClass.persistantProperties) {
            // If the property is a complex type (Domain, Collection, ...), the JSON is formed to
            // match the ES mapping convention
            // Associations with other domain class are indexed only if those instance are searchable as well

            println "Check ${prop.name}..." + instance."${prop.name}"?.class?.simpleName
            println grailsApplication.domainClasses*.naturalName

            if (instance."${prop.name}"?.class?.simpleName in grailsApplication.domainClasses*.naturalName) {
              println "Indexed ${prop.name} as JSON with value : " + [type: instance."${prop.name}".class?.simpleName, id: instance."${prop.name}".id]
              json.field(prop.name, [type: instance."${prop.name}".class?.simpleName, id: instance."${prop.name}".id])
            } else {
              json.field(prop.name, instance."${prop.name}")
              println "Indexed ${prop.name} string"
            }
          }
          json.endObject()
          println json.toString()
          /*client.index {
            index indexValue
            type name
            id instance.id.toString()
            source(json)
          }*/
          client.index(indexRequest(indexValue).type(name).id(instance.id.toString()).source(json)
          )
          println "Indexed domain type ${name} of id ${instance.id} and source ${json}"
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
      try {
        elasticSearchHelper.withElasticSearch { GClient client ->
          Class clazz = instance.class
          String name = GrailsNameUtils.getPropertyName(clazz)
          def indexValue = clazz.getPackage().name ?: name
          def builder = {
            index indexValue
            type name
            id instance.id.toString()
          }
          def response = client.get(builder).response
          println "After a GET : ${response.source}"
          if (response.source.message) {
            println response.source.user
            println(response.id())
          }
        }
      } catch (e) {
        GrailsUtil.deepSanitize(e)
        throw new IndexException("Failed to reget [${instance}] : ${e.message}", e)
      }
    }
  }
}

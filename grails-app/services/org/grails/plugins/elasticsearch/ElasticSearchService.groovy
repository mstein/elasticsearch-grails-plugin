package org.grails.plugins.elasticsearch

import grails.util.GrailsNameUtils
import grails.util.GrailsUtil
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.plugins.support.aware.GrailsApplicationAware
import static org.elasticsearch.client.Requests.indexRequest
import org.elasticsearch.client.Client
import org.grails.plugins.elasticsearch.exception.IndexException
import org.grails.plugins.elasticsearch.util.ThreadWithSession
import static org.elasticsearch.client.Requests.deleteRequest
import org.elasticsearch.action.search.SearchType
import static org.elasticsearch.client.Requests.searchRequest
import static org.elasticsearch.search.builder.SearchSourceBuilder.searchSource
import static org.elasticsearch.index.query.xcontent.QueryBuilders.queryString
import org.apache.commons.logging.LogFactory

class ElasticSearchService implements GrailsApplicationAware {
  static LOG = LogFactory.getLog("org.grails.plugins.elasticSearch.ElasticSearchService")
  GrailsApplication grailsApplication
  def elasticSearchHelper
  def sessionFactory
  def jsonDomainFactory
  def persistenceInterceptor
  def domainInstancesRebuilder

  boolean transactional = false

  def search(String query, Map params = [from: 0, size: 60, explain: true] ) {
    elasticSearchHelper.withElasticSearch { Client client ->
      try {
        def request
        if(params.indices){
          request = searchRequest(params.indices)
        } else {
          request = searchRequest()
        }
        if(params.type){
          request.types(params.type)
        }
        request.searchType(SearchType.DFS_QUERY_THEN_FETCH)
                        .source(searchSource().query(queryString(query))
                        .from(params.from ?: 0)
                        .size(params.size ?: 60)
                        .explain(params.containsKey('explain') ? params.explain : true))
        def response = client.search(request).actionGet()
        def searchHits = response.hits()
        def result = [:]
        result.total = searchHits.totalHits()

        LOG.info("Found ${result.total ?: 0} result(s).")

        // Convert the hits back to their initial type
        result.searchResults = domainInstancesRebuilder.buildResults(searchHits.hits())

        return result
      } catch (e) {
        e.printStackTrace()
        return [searchResult: [], total: 0]
      }
    }
  }

  void indexDomain(instance) {
    indexInBackground(instance, 0)
  }

  void deleteDomain(instance) {
    deleteInBackground(instance, 0)
  }

  private Thread deleteInBackground(instance, attempts) {
    return Thread.start {
      try {
        elasticSearchHelper.withElasticSearch { Client client ->
          Class clazz = instance.class
          String name = GrailsNameUtils.getPropertyName(clazz)
          def indexValue = clazz.package.name ?: name
          client.delete(
                  deleteRequest(indexValue)
                          .id(instance.id.toString())
                          .type(name)
          )
          LOG.info("Deleted domain document type ${name} of id ${instance.id}")
        }
      } catch (e) {
        if (attempts < 5) {
          sleep 10000
          indexInBackground(instance, ++attempts)
        } else {
          GrailsUtil.deepSanitize(e)
          throw new IndexException("Failed to delete domain index [${instance}] after 5 retry attempts: ${e.message}", e)
        }
      }
    }
  }

  private Thread indexInBackground(instance, attempts) {
    return ThreadWithSession.startWithSession(sessionFactory, persistenceInterceptor) {
      try {
        elasticSearchHelper.withElasticSearch { Client client ->
          Class clazz = instance.class
          String name = GrailsNameUtils.getPropertyName(clazz)
          def indexValue = clazz.package.name ?: name

          def json = jsonDomainFactory.buildJSON(instance)
          client.index(
                  indexRequest(indexValue)
                          .type(name)
                          .id(instance.id.toString())
                          .source(json)
          )
          LOG.info("Indexed domain type ${name} of id ${instance.id} and source ${json.string()}")
        }
      } catch (e) {
        if (attempts < 5) {
          sleep 10000
          indexInBackground(instance, ++attempts)
        } else {
          GrailsUtil.deepSanitize(e)
          throw new IndexException("Failed to index domain instance [${instance}] after 5 retry attempts: ${e.message}", e)
        }
      }
    }
  }

}

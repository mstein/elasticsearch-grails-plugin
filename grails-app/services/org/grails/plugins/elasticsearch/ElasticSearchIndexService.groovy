package org.grails.plugins.elasticsearch

import grails.util.GrailsNameUtils
import grails.util.GrailsUtil
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.plugins.support.aware.GrailsApplicationAware
import static org.elasticsearch.client.Requests.indexRequest
import org.elasticsearch.client.Client

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

  private Thread deleteInBackground(instance, count) {
    return Thread.start {
      elasticSearchHelper.withElasticSearch { client ->
        // TODO : delete in background

      }
    }
  }

  private Thread indexInBackground(instance, count) {
    return ThreadWithSession.startWithSession(sessionFactoryInstance) {
      try {
        elasticSearchHelper.withElasticSearch { Client client ->
          Class clazz = instance.class
          String name = GrailsNameUtils.getPropertyName(clazz)
          def indexValue = clazz.getPackage().name ?: name

          def json = jsonDomainFactory.buildJSON(instance)
          client.index(
                  indexRequest(indexValue)
                          .type(name)
                          .id(instance.id.toString())
                          .source(json)
          )
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

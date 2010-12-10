package org.grails.plugins.elasticsearch

import org.elasticsearch.client.Client

/**
 *
 * @author Graeme Rocher
 */
class ElasticSearchHelper {

  Client elasticSearchClient

  def withElasticSearch(Closure callable) {
    callable.call(elasticSearchClient)
  }

}

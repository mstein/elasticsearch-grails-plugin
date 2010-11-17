package org.grails.plugins.elasticsearch

/**
 *
 * @author Graeme Rocher
 */
class ElasticSearchHelper {

  org.elasticsearch.groovy.node.GNode elasticSearchNode

  def withElasticSearch(Closure callable) {
    callable.call(elasticSearchNode.client)
  }

}

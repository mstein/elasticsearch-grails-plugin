package org.grails.plugins.elasticsearch

/**
 *
 * @author Graeme Rocher
 */
class ElasticSearchHelper {

  org.elasticsearch.node.Node elasticSearchNode

  def withElasticSearch(Closure callable) {
    callable.call(elasticSearchNode.client())
  }

}

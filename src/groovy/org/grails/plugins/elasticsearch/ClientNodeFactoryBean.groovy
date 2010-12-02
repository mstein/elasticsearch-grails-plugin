package org.grails.plugins.elasticsearch

import org.springframework.beans.factory.FactoryBean
import static org.elasticsearch.node.NodeBuilder.*
import org.elasticsearch.groovy.node.GNodeBuilder
import org.apache.commons.lang.NotImplementedException

class ClientNodeFactoryBean implements FactoryBean {
  def elasticSearchContextHolder
  static SUPPORTED_MODES = ['local', 'transport', 'node']

  Object getObject() {
    // Retrieve client mode, default is "node"
    def clientMode = elasticSearchContextHolder.config.client.mode ?: 'node'
    if(!(clientMode in SUPPORTED_MODES)) {
      throw new IllegalArgumentException('Client mode invalid.')
    }

    def nb = nodeBuilder()
    switch(clientMode) {
      case 'local':
        nb.local(true)
        break;
      case 'transport':
        // TODO : TransportClient
        throw new NotImplementedException('Transport mode is not yet supported.')
      case 'node':
      default:
        nb.client(true)
        break;
    }
    nb.node()
  }

  Class getObjectType() {
    return org.elasticsearch.node.Node
  }

  boolean isSingleton() {
    return true
  }
}

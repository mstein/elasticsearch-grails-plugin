package org.grails.plugins.elasticsearch

import org.springframework.beans.factory.FactoryBean
import static org.elasticsearch.node.NodeBuilder.*
import org.apache.commons.lang.NotImplementedException
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.transport.InetSocketTransportAddress

class ClientNodeFactoryBean implements FactoryBean {
  def elasticSearchContextHolder
  static SUPPORTED_MODES = ['local', 'transport', 'node']

  Object getObject() {
    // Retrieve client mode, default is "node"
    def clientMode = elasticSearchContextHolder.config.client.mode ?: 'node'
    if(!(clientMode in SUPPORTED_MODES)) {
      throw new IllegalArgumentException("Invalid client mode, expected values were ${SUPPORTED_MODES}.")
    }

    def nb = nodeBuilder()
    def transportClient = null
    switch(clientMode) {
      case 'local':
        nb.local(true)
        break;
      case 'transport':
        transportClient = new TransportClient()
        if(!elasticSearchContextHolder.config.client.hosts){
          transportClient.addTransportAddress(new InetSocketTransportAddress('localhost', 9300))
        } else {
          elasticSearchContextHolder.config.client.hosts.each {
            transportClient.addTransportAddress(new InetSocketTransportAddress(it.host, it.port))
          }
        }
        break;
      case 'node':
      default:
        nb.client(true)
        break;
    }
    if(transportClient){
      return transportClient
    } else {
      return nb.node().client()
    }
  }

  Class getObjectType() {
    return org.elasticsearch.client.Client
  }

  boolean isSingleton() {
    return true
  }
}

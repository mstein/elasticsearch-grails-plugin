/*
 * Copyright 2002-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.grails.plugins.elasticsearch

import static org.elasticsearch.node.NodeBuilder.nodeBuilder

import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.FactoryBean
import org.springframework.core.io.support.PathMatchingResourcePatternResolver

class ClientNodeFactoryBean implements FactoryBean {

    static final SUPPORTED_MODES = ['local', 'transport', 'node', 'dataNode']

    private static final Logger LOG = LoggerFactory.getLogger(this)

    ElasticSearchContextHolder elasticSearchContextHolder
	 def node

    Object getObject() {
        // Retrieve client mode, default is "node"
        def clientMode = elasticSearchContextHolder.config.client.mode ?: 'node'
        if (!(clientMode in SUPPORTED_MODES)) {
            throw new IllegalArgumentException("Invalid client mode, expected values were ${SUPPORTED_MODES}.")
        }
        def nb = nodeBuilder()

        def configFile = elasticSearchContextHolder.config.bootstrap.config.file
        if (configFile) {
            LOG.info "Looking for bootstrap configuration file at: $configFile"
            def resource = new PathMatchingResourcePatternResolver().getResource(configFile)
            nb.settings(ImmutableSettings.settingsBuilder().loadFromUrl(resource.URL))
        }

        def transportClient
        // Cluster name
        if (elasticSearchContextHolder.config.cluster.name) {
            nb.clusterName(elasticSearchContextHolder.config.cluster.name)
        }

        // Path to the data folder of ES
        def dataPath = elasticSearchContextHolder.config.path.data
        if (dataPath) {
            nb.settings.put('path.data', dataPath as String)
            LOG.info "Using ElasticSearch data path: ${dataPath}"
        }

        // Configure the client based on the client mode
        switch (clientMode) {
            case 'transport':
                def transportSettings = ImmutableSettings.settingsBuilder()
                // Use the "sniff" feature of transport client ?
                if (elasticSearchContextHolder.config.client.transport.sniff) {
                    transportSettings.put("client.transport.sniff", true)
                }
                if (elasticSearchContextHolder.config.cluster.name) {
                    transportSettings.put('cluster.name', elasticSearchContextHolder.config.cluster.name.toString())
                }
                transportClient = new TransportClient(transportSettings)

                // Configure transport addresses
                if (!elasticSearchContextHolder.config.client.hosts) {
                    transportClient.addTransportAddress(new InetSocketTransportAddress('localhost', 9300))
                } else {
                    elasticSearchContextHolder.config.client.hosts.each {
                        transportClient.addTransportAddress(new InetSocketTransportAddress(it.host, it.port))
                    }
                }
                break

            case 'local':
                // Determines how the data is stored (on disk, in memory, ...)
                def storeType = elasticSearchContextHolder.config.index.store.type
                if (storeType) {
                    nb.settings().put('index.store.type', storeType as String)
                    LOG.debug "Local ElasticSearch client with store type of ${storeType} configured."
                } else {
                    LOG.debug "Local ElasticSearch client with default store type configured."
                }
                def gatewayType = elasticSearchContextHolder.config.gateway.type
                if (gatewayType) {
                    nb.settings().put('gateway.type', gatewayType as String)
                    LOG.debug "Local ElasticSearch client with gateway type of ${gatewayType} configured."
                } else {
                    LOG.debug "Local ElasticSearch client with default gateway type configured."
                }
                def queryParsers = elasticSearchContextHolder.config.index.queryparser
                if (queryParsers) {
                    queryParsers.each { type, clz ->
                        nb.settings().put("index.queryparser.types.${type}".toString(), clz)
                    }
                }

                def pluginsDirectory = elasticSearchContextHolder.config.path.plugins
                if(pluginsDirectory){
                    nb.settings().put('path.plugins', pluginsDirectory as String)
                }
                nb.local(true)
                break

            case 'dataNode':
                def storeType = elasticSearchContextHolder.config.index.store.type
                if (storeType) {
                  nb.settings().put('index.store.type', storeType as String)
                  LOG.debug "DataNode ElasticSearch client with store type of ${storeType} configured."
                } else {
                  LOG.debug "DataNode ElasticSearch client with default store type configured."
                }
                def queryParsers = elasticSearchContextHolder.config.index.queryparser
                if (queryParsers) {
                  queryParsers.each { type, clz ->
                    nb.settings().put("index.queryparser.types.${type}".toString(), clz)
                  }
                }
                if (elasticSearchContextHolder.config.discovery.zen.ping.unicast.hosts) {
                  nb.settings().put("discovery.zen.ping.unicast.hosts", elasticSearchContextHolder.config.discovery.zen.ping.unicast.hosts)
                }

                nb.client(false)
                nb.data(true)
                break

            case 'node':
            default:
                nb.client(true)
                break
        }
        if (transportClient) {
            return transportClient
        }
		
	//Inject http settings...
	if(elasticSearchContextHolder.config.http){
		flattenMap(elasticSearchContextHolder.config.http).each { p ->
			nb.settings().put("http.${p.key}",p.value as String)
		}
	}
		
        // Avoiding this:
        node = nb.node()
        node.start()
        def client = node.client()
        // Wait for the cluster to become alive.
        //            LOG.info "Waiting for ElasticSearch GREEN status."
        //            client.admin().cluster().health(new ClusterHealthRequest().waitForGreenStatus()).actionGet()
        return client
    }
    //From http://groovy.329449.n5.nabble.com/Flatten-Map-using-closure-td364360.html
    def flattenMap(map){
	    [:].putAll(map.entrySet().flatten{ it.value instanceof Map ? it.value.collect{ k, v -> new MapEntry(it.key + '.' + k, v)} : it })
    }
    Class getObjectType() {
        return org.elasticsearch.client.Client
    }

    boolean isSingleton() {
        return true
    }

    def shutdown() {
        if (elasticSearchContextHolder.config.client.mode == 'local' || elasticSearchContextHolder.config.client.mode == 'dataNode' && node) {
            LOG.info "Stopping embedded ElasticSearch."
            node.close()        // close() seems to be more appropriate than stop()
        }
    }
}

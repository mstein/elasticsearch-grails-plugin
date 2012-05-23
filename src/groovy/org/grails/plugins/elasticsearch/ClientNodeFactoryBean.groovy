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

import org.springframework.beans.factory.FactoryBean
import static org.elasticsearch.node.NodeBuilder.*
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.apache.log4j.Logger

class ClientNodeFactoryBean implements FactoryBean {
    def elasticSearchContextHolder
    static SUPPORTED_MODES = ['local', 'transport', 'node']
    private static final LOG = Logger.getLogger(ClientNodeFactoryBean)

    def node

    Object getObject() {
        // Retrieve client mode, default is "node"
        def clientMode = elasticSearchContextHolder.config.client.mode ?: 'node'
        if (!(clientMode in SUPPORTED_MODES)) {
            throw new IllegalArgumentException("Invalid client mode, expected values were ${SUPPORTED_MODES}.")
        }

        def nb = nodeBuilder()
        def transportClient = null
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
                // Determines how the data is store (on disk, in memory, ...)
                def storeType = elasticSearchContextHolder.config.index.store.type
                if (storeType) {
                    nb.settings().put('index.store.type', storeType as String)
                    LOG.debug "Local ElasticSearch client with store type of ${storeType} configured."
                } else {
                    LOG.debug "Local ElasticSearch client with default store type configured."
                }
                def queryParsers = elasticSearchContextHolder.config.index.queryparser
                if (queryParsers) {
                    queryParsers.each { type, clz ->
                        nb.settings().put("index.queryparser.types.${type}".toString(), clz)
                    }
                }
                nb.local(true)
                break

            case 'node':
            default:
                nb.client(true)
                break
        }
        if (transportClient) {
            return transportClient
        } else {
            // Avoiding this:
            node = nb.node()
            node.start()
            def client = node.client()
            // Wait for the cluster to become alive.
            //            LOG.info "Waiting for ElasticSearch GREEN status."
            //            client.admin().cluster().health(new ClusterHealthRequest().waitForGreenStatus()).actionGet()
            return client
        }
    }

    Class getObjectType() {
        return org.elasticsearch.client.Client
    }

    boolean isSingleton() {
        return true
    }

    def shutdown() {
        if (elasticSearchContextHolder.config.client.mode == 'local' && node) {
            LOG.info "Stopping embedded ElasticSearch."
            node.close()        // close() seems to be more appropriate than stop()
        }
    }
}

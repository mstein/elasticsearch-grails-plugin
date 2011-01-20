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
import org.apache.commons.lang.NotImplementedException
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest
import org.apache.log4j.Logger

class ClientNodeFactoryBean implements FactoryBean {
    def elasticSearchContextHolder
    static SUPPORTED_MODES = ['local', 'transport', 'node']
    static LOG = Logger.getLogger(ClientNodeFactoryBean)

    Object getObject() {
        // Retrieve client mode, default is "node"
        def clientMode = elasticSearchContextHolder.config.client.mode ?: 'node'
        if (!(clientMode in SUPPORTED_MODES)) {
            throw new IllegalArgumentException("Invalid client mode, expected values were ${SUPPORTED_MODES}.")
        }

        def nb = nodeBuilder()
        def transportClient = null
        switch (clientMode) {
            case 'local':
                nb.local(true)
                break;
            case 'transport':
                transportClient = new TransportClient()
                if (!elasticSearchContextHolder.config.client.hosts) {
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
        if (transportClient) {
            return transportClient
        } else {
            // Avoiding this:
            // http://groups.google.com/a/elasticsearch.com/group/users/browse_thread/thread/2bb5d8dd6dd9b80b/e7db9e63fc305133?show_docid=e7db9e63fc305133&fwc=1
            def client = nb.node().client()
            // Wait for the cluster to become alive.
            LOG.info "Waiting for ElasticSearch green status."
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
}

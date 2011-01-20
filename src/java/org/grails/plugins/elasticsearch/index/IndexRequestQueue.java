/*
 * Copyright 2002-2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.plugins.elasticsearch.index;

import org.apache.log4j.Logger;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.action.bulk.BulkRequestBuilder;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.grails.plugins.elasticsearch.ElasticSearchContextHolder;
import org.grails.plugins.elasticsearch.conversion.JSONDomainFactory;
import org.grails.plugins.elasticsearch.exception.IndexException;
import org.grails.plugins.elasticsearch.mapping.SearchableClassMapping;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;

/**
 * Holds objects to be indexed.
 * We don't want to hold object references, so all domain instances have to be JSONed before
 * putting into queue.
 *
 * This is shared class, so need to be thread-safe.
 */
public class IndexRequestQueue {

    private static final Logger LOG = Logger.getLogger(IndexRequestQueue.class);

    private JSONDomainFactory jsonDomainFactory;
    private ElasticSearchContextHolder elasticSearchContextHolder;
    private Client elasticSearchClient;

    /**
     * A map containing the pending index requests.
     */
    Map<IndexEntityKey, Object> indexRequests = new HashMap<IndexEntityKey, Object>();

    /**
     * A set containing the pending delete requests.
     */
    Set<IndexEntityKey> deleteRequests = new HashSet<IndexEntityKey>();

    /**
     * No-args constructor.
     */
    public IndexRequestQueue() {
    }

    public void setJsonDomainFactory(JSONDomainFactory jsonDomainFactory) {
        this.jsonDomainFactory = jsonDomainFactory;
    }

    public void setElasticSearchContextHolder(ElasticSearchContextHolder elasticSearchContextHolder) {
        this.elasticSearchContextHolder = elasticSearchContextHolder;
    }

    public void setElasticSearchClient(Client elasticSearchClient) {
        this.elasticSearchClient = elasticSearchClient;
    }

    public void addIndexRequest(Object instance) {
        synchronized (this) {
            indexRequests.put(new IndexEntityKey(instance), instance);
        }
    }

    public void addDeleteRequest(Object instance) {
        synchronized (this) {
            deleteRequests.add(new IndexEntityKey(instance));
        }
    }

    public XContentBuilder toJSON(Object instance) {
        try {
            return jsonDomainFactory.buildJSON(instance);
        } catch (Exception e) {
            throw new IndexException("Failed to marshall domain instance [" + instance + "]", e);
        }
    }

    /**
     * Execute pending requests and clear both index & delete pending queues.
     */
    public void executeRequests() {
        // If there are domain instances that are both in the index requests & delete requests list,
        // they are directly deleted.
        Map<IndexEntityKey, Object> toIndex = new LinkedHashMap<IndexEntityKey, Object>();
        Set<IndexEntityKey> toDelete = new HashSet<IndexEntityKey>();

        // Copy existing queue to ensure we are interfering with incoming requests.
        synchronized (this) {
            toIndex.putAll(indexRequests);
            toDelete.addAll(deleteRequests);
            indexRequests.clear();
            deleteRequests.clear();
        }

        toIndex.keySet().removeAll(toDelete);

        if (toIndex.isEmpty() && toDelete.isEmpty()) {
            return;
        }

        BulkRequestBuilder bulkRequestBuilder = new BulkRequestBuilder(elasticSearchClient);

        // Execute index requests
        for (Map.Entry<IndexEntityKey, Object> entry : toIndex.entrySet()) {
            SearchableClassMapping scm = elasticSearchContextHolder.getMappingContextByType(entry.getKey().getClazz());
            XContentBuilder json = toJSON(entry.getValue());
            bulkRequestBuilder.add(
                    new IndexRequest(scm.getIndexName())
                            .type(scm.getElasticTypeName())
                            .id(entry.getKey().getId().toString())      // how about composite keys?
                            .source(json));
            if (LOG.isDebugEnabled()) {
                try {
                    LOG.debug("Indexed domain type " + entry.getKey().getClazz() + " of id " + entry.getKey().getId() +
                            " and source " + json.string());
                } catch (IOException e) {}
            }
        }

        // Execute delete requests
        for(IndexEntityKey key : toDelete) {
            SearchableClassMapping scm = elasticSearchContextHolder.getMappingContextByType(key.getClazz());
            bulkRequestBuilder.add(
                        new DeleteRequest(scm.getIndexName())
                                .type(scm.getElasticTypeName())
                                .id(key.getId().toString())
            );
        }

        if (bulkRequestBuilder.numberOfActions() > 0) {
            try {
                bulkRequestBuilder.execute().actionGet();
            } catch (Exception e) {
                throw new IndexException("Failed to index/delete " + bulkRequestBuilder.numberOfActions(), e);
            }
        }

    }

    class IndexEntityKey implements Serializable {

        private final Serializable id;
        private final Class clazz;

        IndexEntityKey(Object instance) {
            this.clazz = instance.getClass();
            SearchableClassMapping scm = elasticSearchContextHolder.getMappingContextByType(this.clazz);
            if (scm == null) {
                throw new IllegalArgumentException("Class " + clazz + " is not a searchable domain class.");
            }
            this.id = (Serializable) InvokerHelper.invokeMethod(instance, "ident", null);
        }

        public Serializable getId() {
            return id;
        }

        public Class getClazz() {
            return clazz;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            IndexEntityKey that = (IndexEntityKey) o;

            if (!clazz.equals(that.clazz)) return false;
            if (!id.equals(that.id)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = id.hashCode();
            result = 31 * result + clazz.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "IndexEntityKey{" +
                    "id=" + id +
                    ", clazz=" + clazz +
                    '}';
        }
    }
}

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

import grails.util.GrailsNameUtils
import grails.util.GrailsUtil
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.plugins.support.aware.GrailsApplicationAware
import static org.elasticsearch.client.Requests.indexRequest
import org.elasticsearch.client.Client
import org.grails.plugins.elasticsearch.exception.IndexException
import org.grails.plugins.elasticsearch.util.ThreadWithSession
import static org.elasticsearch.client.Requests.deleteRequest
import org.elasticsearch.action.search.SearchType
import static org.elasticsearch.client.Requests.searchRequest
import static org.elasticsearch.search.builder.SearchSourceBuilder.searchSource
import static org.elasticsearch.index.query.xcontent.QueryBuilders.queryString
import org.apache.log4j.Logger
import org.grails.plugins.elasticsearch.mapping.SearchableClassMapping

public class ElasticSearchService implements GrailsApplicationAware {

    static LOG = Logger.getLogger("org.grails.plugins.elasticSearch.ElasticSearchService")

    GrailsApplication grailsApplication
    def elasticSearchHelper
    def sessionFactory
    def jsonDomainFactory
    def persistenceInterceptor
    def domainInstancesRebuilder
    def elasticSearchContextHolder

    boolean transactional = false

    def search(String query, Map params = [from: 0, size: 60, explain: true]) {
        elasticSearchHelper.withElasticSearch { Client client ->
            def request
            if (params.indices) {
                request = searchRequest(params.indices)
            } else {
                request = searchRequest()
            }
            if (params.types) {
                request.types(params.types)
            }
            request.searchType(SearchType.DFS_QUERY_THEN_FETCH).source(searchSource().query(queryString(query)).from(params.from ?: 0).size(params.size ?: 60).explain(params.containsKey('explain') ? params.explain : true))
            def response = client.search(request).actionGet()
            def searchHits = response.hits()
            def result = [:]
            result.total = searchHits.totalHits()

            LOG.info("Found ${result.total ?: 0} result(s).")

            // Convert the hits back to their initial type
            result.searchResults = domainInstancesRebuilder.buildResults(searchHits)

            return result
//      } catch (e) {
//        e.printStackTrace()
//        return [searchResults: [], total: 0]
//      }
        }
    }

    void indexDomain(instance) {
        indexInBackground(instance, 0)
    }

    void deleteDomain(instance) {
        deleteInBackground(instance, 0)
    }

    /**
     * Index ALL searchable instances.
     * VERY SLOW until bulk indexing is done.
     * @param options indexing options
     */
    public void index(Map options = [:]) {
        def clazz = options?.class
        def mappings = []
        if (clazz) {
            mappings << elasticSearchContextHolder.getMappingContextByType(clazz)
        } else {
            mappings = elasticSearchContextHolder.mapping
        }
        mappings.each { scm ->
            LOG.debug("Indexing all instances of ${scm.domainClass}")
            scm.domainClass.getAll().each { indexDomain(it) }
        }
    }

    private Thread deleteInBackground(instance, attempts) {
        return Thread.start {
            try {
                elasticSearchHelper.withElasticSearch { Client client ->
                    Class clazz = instance.class
                    String name = GrailsNameUtils.getPropertyName(clazz)
                    def indexValue = clazz.package.name ?: name
                    client.delete(
                            deleteRequest(indexValue).id(instance.id.toString()).type(name)
                    )
                    LOG.info("Deleted domain document type ${name} of id ${instance.id}")
                }
            } catch (e) {
                if (attempts < 5) {
                    sleep 10000
                    indexInBackground(instance, ++attempts)
                } else {
                    GrailsUtil.deepSanitize(e)
                    throw new IndexException("Failed to delete domain index [${instance}] after 5 retry attempts: ${e.message}", e)
                }
            }
        }
    }

    private Thread indexInBackground(instance, attempts) {
        return ThreadWithSession.startWithSession(sessionFactory, persistenceInterceptor) {
            def json
            try {
                json = jsonDomainFactory.buildJSON(instance)
            } catch (e) {
                throw new IndexException("Failed to marshall domain instance [${instance}]", e)
            }
            try {
                elasticSearchHelper.withElasticSearch { Client client ->
                    Class clazz = instance.class
                    SearchableClassMapping scm = elasticSearchContextHolder.getMappingContextByType(clazz)
                    def indexValue = scm.indexName
                    String name = scm.elasticTypeName

                    client.index(
                            indexRequest(indexValue).type(name).id(instance.id.toString()).source(json)
                    )
                    LOG.info("Indexed domain type ${name} of id ${instance.id} and source ${json.string()}")
                }
            } catch (e) {
                if (attempts < 5) {
                    sleep 10000
                    indexInBackground(instance, ++attempts)
                } else {
                    GrailsUtil.deepSanitize(e)
                    throw new IndexException("Failed to index domain instance [${instance}] after 5 retry attempts: ${e.message}", e)
                }
            }
        }
    }

}

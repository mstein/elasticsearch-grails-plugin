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

import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.plugins.support.aware.GrailsApplicationAware
import org.elasticsearch.client.Client
import org.elasticsearch.action.search.SearchType
import static org.elasticsearch.client.Requests.searchRequest
import static org.elasticsearch.search.builder.SearchSourceBuilder.searchSource
import static org.elasticsearch.index.query.xcontent.QueryBuilders.queryString
import org.apache.log4j.Logger
import org.elasticsearch.index.query.xcontent.XContentQueryBuilder

public class ElasticSearchService implements GrailsApplicationAware {

    static LOG = Logger.getLogger("org.grails.plugins.elasticSearch.ElasticSearchService")

    GrailsApplication grailsApplication
    def elasticSearchHelper
    def sessionFactory
    def persistenceInterceptor
    def domainInstancesRebuilder
    def elasticSearchContextHolder
    def indexRequestQueue

    boolean transactional = false

    // todo make it more Groovish, ie allow passing a building closure instead
    def search(XContentQueryBuilder qb, Map params = [:]) {
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
            request.searchType(SearchType.DFS_QUERY_THEN_FETCH)
                    .source(searchSource()
                    .query(qb)
                    .from(params.from ?: 0)
                    .size(params.size ?: 60)
                    .explain(params.containsKey('explain') ? params.explain : true))
            def response = client.search(request).actionGet()
            def searchHits = response.hits()
            def result = [:]
            result.total = searchHits.totalHits()

            LOG.info("Found ${result.total ?: 0} result(s).")

            // Convert the hits back to their initial type
            result.searchResults = domainInstancesRebuilder.buildResults(searchHits)

            return result
        }
    }

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
            mappings = elasticSearchContextHolder.mapping.values()
        }
        int count = 0
        mappings.each { scm ->
            if (scm.root) {
                // only index root instances.
                LOG.debug("Indexing all instances of ${scm.domainClass}")
                scm.domainClass.metaClass.invokeStaticMethod(scm.domainClass.clazz, "getAll", null).each { indexRequestQueue.addIndexRequest(it); count++ }
            }
        }
        log.debug "Bulk index: ${count} instances."
        indexRequestQueue.executeRequests();
    }

}

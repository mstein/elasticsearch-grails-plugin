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
import org.elasticsearch.action.count.CountRequest
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchType
import org.elasticsearch.action.support.QuerySourceBuilder
import org.elasticsearch.client.Client
import org.elasticsearch.index.query.QueryBuilder
import org.elasticsearch.index.query.QueryStringQueryBuilder
import org.elasticsearch.index.query.FilterBuilder
import org.elasticsearch.search.SearchHit
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.search.highlight.HighlightBuilder
import org.elasticsearch.search.sort.SortBuilder
import org.elasticsearch.search.sort.SortOrder
import org.grails.plugins.elasticsearch.mapping.SearchableClassMapping
import org.grails.plugins.elasticsearch.util.GXContentBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import static org.elasticsearch.index.query.QueryBuilders.queryString
import static org.elasticsearch.index.query.QueryStringQueryBuilder.Operator

class ElasticSearchService implements GrailsApplicationAware {
    static final Logger LOG = LoggerFactory.getLogger(this)

    private static final int INDEX_REQUEST = 0
    private static final int DELETE_REQUEST = 1

    GrailsApplication grailsApplication
    def elasticSearchHelper
    def domainInstancesRebuilder
    def elasticSearchContextHolder
    def indexRequestQueue

    static transactional = false

    /**
     * Global search using Query DSL builder.
     *
     * @param params Search parameters
     * @param closure Query closure
     * @return search results
     */
    def search(Map params, Closure query, filter = null) {
        SearchRequest request = buildSearchRequest(query, filter, params)
        search(request, params)
    }

    /**
     * Alias for the search(Map params, Closure query) signature.
     *
     * @param query Query closure
     * @param params Search parameters
     * @return search results
     */
    def search(Closure query, filter = null, Map params = [:]) {
        search(params, query, filter)
    }

    def search(Closure query, Map params) {
        search(params, query)
    }

	/**
	 * Alias for the search(Map params, QueryBuilder query, Closure filter) signature
	 * 
	 * @param query QueryBuilder query
	 * @return
	 */
    def search(QueryBuilder query, filter = null, Map params = [:]) {
        search(params, query, filter)
    }

    def search(Map params, QueryBuilder query, filter = null) {
        SearchRequest request = buildSearchRequest(query, filter, params)
        search(request, params)
    }

    /**
     * Global search with a text query.
     *
     * @param query The search query. Will be parsed by the Lucene Query Parser.
     * @param params Search parameters
     * @return A Map containing the search results
     */
    def search(String query, Map params = [:]) {
        SearchRequest request = buildSearchRequest(query, null, params)
        search(request, params)
    }
	
	def search(String query, filter, Map params = [:]){
		SearchRequest request = buildSearchRequest(query, filter, params)
		search(request, params)
	}

    /**
     * Returns the number of hits for a peculiar query
     *
     * @param query
     * @param params
     * @return An Integer representing the number of hits for the query
     */
    Integer countHits(String query, Map params = [:]) {
        CountRequest request = buildCountRequest(query, params)
        count(request, params)
    }

    /**
     * Returns the number of hits for a peculiar query
     *
     * @param query
     * @param params
     * @return An Integer representing the number of hits for the query
     */
    Integer countHits(Map params, Closure query) {
        CountRequest request = buildCountRequest(query, params)
        count(request, params)
    }

    /**
     * Returns the number of hits for a peculiar query
     *
     * @param query
     * @param params
     * @return An Integer representing the number of hits for the query
     */
    Integer countHits(Closure query, Map params = [:]) {
        countHits(params, query)
    }

    /**
     * Indexes all searchable instances of the specified class.
     * If call without arguments, index ALL searchable instances.
     * Note: The indexRequestQueue is using the bulk API so it is optimized.
     * Todo: should be used along with serializable IDs, but we have to deal with composite IDs beforehand
     *
     * @param options indexing options
     */
    void index(Map options) {
        doBulkRequest(options, INDEX_REQUEST)
    }

    /**
     * An alias for index(class:[MyClass1, MyClass2])
     *
     * @param domainClass List of searchable class
     */
    void index(Class... domainClass) {
        index(class: (domainClass as Collection<Class>))
    }

    /**
     * Indexes domain class instances
     *
     * @param instances A Collection of searchable instances to index
     */
    void index(Collection<GroovyObject> instances) {
        doBulkRequest(instances, INDEX_REQUEST)
    }

    /**
     * Alias for index(Object instances)
     *
     * @param instances
     */
    void index(GroovyObject... instances) {
        index(instances as Collection<GroovyObject>)
    }

    /**
     * Unindexes all searchable instances of the specified class.
     * If call without arguments, unindex ALL searchable instances.
     * Note: The indexRequestQueue is using the bulk API so it is optimized.
     * Todo: should be used along with serializable IDs, but we have to deal with composite IDs beforehand
     *
     * @param options indexing options
     */
    void unindex(Map options) {
        doBulkRequest(options, DELETE_REQUEST)
    }

    /**
     * An alias for unindex(class:[MyClass1, MyClass2])
     *
     * @param domainClass List of searchable class
     */
    void unindex(Class... domainClass) {
        unindex(class: (domainClass as Collection<Class>))
    }

    /**
     * Unindexes domain class instances
     *
     * @param instances A Collection of searchable instances to index
     */
    void unindex(Collection<GroovyObject> instances) {
        doBulkRequest(instances, DELETE_REQUEST)
    }

    /**
     * Alias for unindex(Object instances)
     *
     * @param instances
     */
    void unindex(GroovyObject... instances) {
        unindex(instances as Collection<GroovyObject>)
    }

    /**
     * Computes a bulk operation on class level.
     *
     * @param options The request options
     * @param operationType The type of the operation (INDEX_REQUEST, DELETE_REQUEST)
     * @return
     */
    private doBulkRequest(Map options, int operationType) {
        def clazz = options.class
        def mappings = []
        if (clazz) {
            if (clazz instanceof Collection) {
                clazz.each { c ->
                    mappings << elasticSearchContextHolder.getMappingContextByType(c)
                }
            } else {
                mappings << elasticSearchContextHolder.getMappingContextByType(clazz)
            }

        } else {
            mappings = elasticSearchContextHolder.mapping.values()
        }
        def maxRes = elasticSearchContextHolder.config.maxBulkRequest ?: 500

        mappings.each { scm ->
            if (scm.root) {
                if (operationType == INDEX_REQUEST) {
                    LOG.debug("Indexing all instances of ${scm.domainClass}")
                } else if (operationType == DELETE_REQUEST) {
                    LOG.debug("Deleting all instances of ${scm.domainClass}")
                }

                // The index is split to avoid out of memory exception
                def count = scm.domainClass.clazz.count() ?: 0
                LOG.debug("Found $count instances of ${scm.domainClass}")

                int nbRun = Math.ceil(count / maxRes)

                LOG.debug("Maximum entries allowed in each bulk request is $maxRes, so indexing is split to $nbRun iterations")

                scm.domainClass.clazz.withNewSession { session ->
                    for (int i = 0; i < nbRun; i++) {
                        def resultToStartFrom = i * maxRes

                        LOG.debug("Bulk index iteration ${i+1}: fetching $maxRes results starting from ${resultToStartFrom}")

                        def results = scm.domainClass.clazz.withCriteria {
                            firstResult(resultToStartFrom)
                            maxResults(maxRes)
                            order('id', 'asc')
                        }

                        LOG.debug("Bulk index iteration ${i+1}: found ${results.size()} results")
                        results.each {
                            if (operationType == INDEX_REQUEST) {
                                indexRequestQueue.addIndexRequest(it)
                                LOG.debug("Adding the document ${it.id} to the index request queue")
                            } else if (operationType == DELETE_REQUEST) {
                                indexRequestQueue.addDeleteRequest(it)
                                LOG.debug("Adding the document ${it.id} to the delete request queue")
                            }
                        }
                        indexRequestQueue.executeRequests()
                        session.clear()

                        log.info("Request iteration ${i+1} out of $nbRun finished")
                    }
                }

            } else {
                LOG.debug("${scm.domainClass.clazz} is not a root searchable class and has been ignored.")
            }
        }
    }

    /**
     * Computes a bulk operation on instance level.
     *
     * @param instances The instance related to the operation
     * @param operationType The type of the operation (INDEX_REQUEST, DELETE_REQUEST)
     * @return
     */
    private void doBulkRequest(Collection<GroovyObject> instances, int operationType) {
        instances.each {
            def scm = elasticSearchContextHolder.getMappingContextByType(it.class)
            if (scm && scm.root) {
                if (operationType == INDEX_REQUEST) {
                    indexRequestQueue.addIndexRequest(it)
                } else if (operationType == DELETE_REQUEST) {
                    indexRequestQueue.addDeleteRequest(it)
                }
            } else {
                LOG.debug("${it.class} is not searchable or not a root searchable class and has been ignored.")
            }
        }
        indexRequestQueue.executeRequests()
    }

    /**
     * Builds a count request
     * @param query
     * @param params
     * @return
     */
    private CountRequest buildCountRequest(query, Map params) {
        CountRequest request = new CountRequest()

        // Handle the query, can either be a closure or a string
        if (query instanceof Closure) {
            request.source(new GXContentBuilder().buildAsBytes(query))
        } else {
            Operator defaultOperator = params['default_operator'] ?: Operator.AND
            QueryStringQueryBuilder builder = queryString(query).defaultOperator(defaultOperator)
            if (params.analyzer) {
                builder.analyzer(params.analyzer)
            }
            request.source(new QuerySourceBuilder().setQuery(builder))
        }

        request
    }

    /**
     * Builds a search request
     *
     * @param params The query parameters
     * @param query The search query, whether a String, a Closure or a QueryBuilder
     * @param filter The search filter, whether a Closure or a FilterBuilder
     * @return The SearchRequest instance
     */
    private SearchRequest buildSearchRequest(query, filter, Map params) {
        SearchSourceBuilder source = new SearchSourceBuilder()

        source.from(params.from ? params.from as int : 0)
                .size(params.size ? params.size as int : 60)
                .explain(params.explain ?: true).minScore(params.min_score ?: 0)

        if (params.sort) {
            def sorters = (params.sort instanceof Collection) ? params.sort : [params.sort]

            sorters.each {
                if (it instanceof SortBuilder) {
                    source.sort(it as SortBuilder)
                } else {
                    source.sort(it, SortOrder.valueOf(params.order?.toUpperCase() ?: "ASC"))
                }
            }
        }

        // Handle the query, can either be a closure or a string
        if (query) {
            setQueryInSource(source, query, params)
        }

        if (filter) {
            setFilterInSource(source, filter, params)
        }

        // Handle highlighting
        if (params.highlight) {
            def highlighter = new HighlightBuilder()
            // params.highlight is expected to provide a Closure.
            def highlightBuilder = params.highlight
            highlightBuilder.delegate = highlighter
            highlightBuilder.resolveStrategy = Closure.DELEGATE_FIRST
            highlightBuilder.call()
            source.highlight highlighter
        }

        source.explain(false)

        SearchRequest request = new SearchRequest()
        request.searchType SearchType.DFS_QUERY_THEN_FETCH
        request.source source

        return request
    }

    SearchSourceBuilder setQueryInSource(SearchSourceBuilder source, String query, Map params = [:]) {
        Operator defaultOperator = params['default_operator'] ?: Operator.AND
        QueryStringQueryBuilder builder = queryString(query).defaultOperator(defaultOperator)
        if (params.analyzer) {
            builder.analyzer(params.analyzer)
        }
        source.query(builder)
    }

    SearchSourceBuilder setQueryInSource(SearchSourceBuilder source, Closure query, Map params = [:]) {
        source.query(new GXContentBuilder().buildAsBytes(query))
    }

    SearchSourceBuilder setQueryInSource(SearchSourceBuilder source, QueryBuilder query, Map params = [:]) {
        source.query(query)
    }
	
	SearchSourceBuilder setFilterInSource(SearchSourceBuilder source, Closure filter, Map params = [:]){
		source.postFilter(new GXContentBuilder().buildAsBytes(filter))
	}
	
	SearchSourceBuilder setFilterInSource(SearchSourceBuilder source, FilterBuilder filter, Map params = [:]){
		source.postFilter(filter)
	}

    /**
     * Computes a search request and builds the results
     *
     * @param request The SearchRequest to compute
     * @param params Search parameters
     * @return A Map containing the search results
     */
    def search(SearchRequest request, Map params) {
        resolveIndicesAndTypes(request, params)
        elasticSearchHelper.withElasticSearch { Client client ->
            LOG.debug 'Executing search request.'
            def response = client.search(request).actionGet()
            LOG.debug 'Completed search request.'
            def searchHits = response.getHits()
            def result = [:]
            result.total = searchHits.totalHits()

            LOG.debug "Search returned ${result.total ?: 0} result(s)."

            // Convert the hits back to their initial type
            result.searchResults = domainInstancesRebuilder.buildResults(searchHits)

            // Extract highlight information.
            // Right now simply give away raw results...
            if (params.highlight) {
                def highlightResults = []
                for (SearchHit hit : searchHits) {
                    highlightResults << hit.highlightFields
                }
                result.highlight = highlightResults
            }

            LOG.debug 'Adding score information to results.'

            //Extract score information
            //Records a map from hits of (hit.id, hit.score) returned in 'scores'
            if (params.score) {
                def scoreResults = [:]
                for (SearchHit hit : searchHits) {
                    scoreResults[(hit.id)] = hit.score
                }
                result.scores = scoreResults
            }

            if (params.sort) {
                def sortValues = [:]
                searchHits.each { SearchHit hit ->
                    sortValues[hit.id] = hit.sortValues
                }
                result.sort = sortValues
            }

            result
        }
    }

    /**
     * Computes a count request and returns the results
     *
     * @param request
     * @param params
     * @return Integer The number of hits for the query
     */
    Integer count(CountRequest request, Map params) {
        resolveIndicesAndTypes(request, params)
        elasticSearchHelper.withElasticSearch { Client client ->
            LOG.debug 'Executing count request.'
            def response = client.count(request).actionGet()
            LOG.debug 'Completed count request.'
            def result = response.count ?: 0

            LOG.debug "${result} hit(s) matched the specified query."

            result
        }
    }
    /**
     * Sets the indices & types properties on SearchRequest & CountRequest
     *
     * @param request
     * @param params
     * @return
     */
    private resolveIndicesAndTypes(request, Map params) {
        assert request instanceof SearchRequest || request instanceof CountRequest

        // Handle the indices.
        if (params.indices) {
            def indices
            if (params.indices instanceof String) {
                // Shortcut for using 1 index only (not a list of values)
                indices = [params.indices.toLowerCase()]
            } else if (params.indices instanceof Class) {
                // Resolved with the class type
                SearchableClassMapping scm = elasticSearchContextHolder.getMappingContextByType(params.indices)
                indices = [scm.queryingIndex]
            } else if (params.indices instanceof Collection<Class>) {
                indices = params.indices.collect { c ->
                    SearchableClassMapping scm = elasticSearchContextHolder.getMappingContextByType(c)
                    scm.queryingIndex
                }
            }
            request.indices((indices ?: params.indices) as String[])
        } else {
            request.indices("_all")
        }

        // Handle the types. Each type must reference a Domain class for now, but we may consider to make it more
        // generic in the future to allow POGO/Map/Whatever indexing/searching
        if (params.types) {
            def types
            if (params.types instanceof String) {
                // Shortcut for using 1 type only with a string
                def scm = elasticSearchContextHolder.getMappingContext(params.types)
                if (!scm) {
                    throw new IllegalArgumentException("Unknown object type: ${params.types}")
                }
                types = [scm.elasticTypeName]
            } else if (params.types instanceof Class) {
                // User can also pass a class to determine the type
                def scm = elasticSearchContextHolder.getMappingContextByType(params.types)
                if (!scm) {
                    throw new IllegalArgumentException("Unknown object type: ${params.types}")
                }
                types = [scm.elasticTypeName]
            } else if (params.types instanceof Collection && !params.types.empty) {
                def firstCollectionElement = params.types.first()

                def typeCollectionMethod
                if (firstCollectionElement instanceof Class) {
                    typeCollectionMethod = { type ->
                        elasticSearchContextHolder.getMappingContextByType(type)
                    }
                } else {
                    typeCollectionMethod = { name ->
                        elasticSearchContextHolder.getMappingContext(name)
                    }
                }
                types = params.types.collect { t ->
                    def scm = typeCollectionMethod.call(t)
                    if (!scm) {
                        throw new IllegalArgumentException("Unknown object type: ${params.types}")
                    }
                    scm.elasticTypeName
                }
            }
            request.types(types as String[])
        }
    }
}

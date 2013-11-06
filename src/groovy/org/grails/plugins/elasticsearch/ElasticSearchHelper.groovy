package org.grails.plugins.elasticsearch

import org.elasticsearch.client.Client

class ElasticSearchHelper {

    Client elasticSearchClient

    def withElasticSearch(Closure callable) {
        callable.call(elasticSearchClient)
    }
}

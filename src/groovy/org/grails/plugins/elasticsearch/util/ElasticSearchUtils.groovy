package org.grails.plugins.elasticsearch.util

import grails.util.Holders

class ElasticSearchUtils {

    static indexDomain(entity){
        def elasticSearchService = Holders.grailsApplication.mainContext.getBean('elasticSearchService')
        elasticSearchService.indexDomain(entity)
    }

    static deleteDomain(entity){
        def elasticSearchService = Holders.grailsApplication.mainContext.getBean('elasticSearchService')
        elasticSearchService.deleteDomain(entity)
    }
}

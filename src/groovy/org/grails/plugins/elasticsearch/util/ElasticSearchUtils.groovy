package org.grails.plugins.elasticsearch.util

import org.codehaus.groovy.grails.commons.ApplicationHolder

class ElasticSearchUtils {
  public static indexDomain(entity){
    def elasticSearchService = ApplicationHolder.application.mainContext.getBean('elasticSearchService')
    elasticSearchService.indexDomain(entity)
  }

  public static deleteDomain(entity){
    def elasticSearchService = ApplicationHolder.application.mainContext.getBean('elasticSearchService')
    elasticSearchService.deleteDomain(entity)
  }
}

package org.grails.plugins.elasticsearch.util

import org.codehaus.groovy.grails.commons.ApplicationHolder

class ElasticSearchUtils {
  public static indexDomain(entity){
    def elasticSearchIndexService = ApplicationHolder.application.mainContext.getBean('elasticSearchIndexService')
    elasticSearchIndexService.indexDomain(entity)
  }
}

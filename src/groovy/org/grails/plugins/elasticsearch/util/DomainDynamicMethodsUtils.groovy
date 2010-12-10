package org.grails.plugins.elasticsearch.util

import org.codehaus.groovy.grails.commons.GrailsDomainClass
import org.grails.plugins.elasticsearch.ElasticSearchContextHolder
import org.grails.plugins.elasticsearch.ElasticSearchHelper
import org.grails.plugins.elasticsearch.mapping.ClosureSearchableDomainClassMapper
import org.grails.plugins.elasticsearch.mapping.ElasticSearchMappingFactory
import org.elasticsearch.client.Client
import org.elasticsearch.indices.IndexAlreadyExistsException
import org.elasticsearch.transport.RemoteTransportException
import org.elasticsearch.client.Requests

class DomainDynamicMethodsUtils {
  static LOG
  /**
   * Resolve the ElasticSearch mapping from the static "searchable" property (closure or boolean) in domain classes
   * @param domainClasses
   * @param applicationContext
   * @return
   */
  static resolveMapping(domainClasses, applicationContext){
    def helper = applicationContext.getBean(ElasticSearchHelper)
    def elasticSearchContextHolder = applicationContext.getBean(ElasticSearchContextHolder)

    domainClasses.each { GrailsDomainClass domainClass ->
      if (domainClass.hasProperty('searchable') && domainClass.getPropertyValue('searchable')) {
        def indexValue = domainClass.packageName ?: domainClass.propertyName
        LOG.debug("Custom mapping for searchable detected in [${domainClass.getPropertyName()}] class, resolving the closure...")
        def closureMapper = new ClosureSearchableDomainClassMapper(domainClass, elasticSearchContextHolder.config)
        def searchableClassMapping = closureMapper.getClassMapping(domainClass, applicationContext.domainClasses as List, domainClass.getPropertyValue('searchable'))
        elasticSearchContextHolder.addMappingContext(searchableClassMapping)

        if (searchableClassMapping.classMapping?.root) {
          def elasticMapping = ElasticSearchMappingFactory.getElasticMapping(searchableClassMapping)
          LOG.debug(elasticMapping.toString())

          helper.withElasticSearch { Client client ->
            try {
              client.admin().indices().prepareCreate(indexValue).execute().actionGet()
              // If the index already exists, ignore the exception
            } catch (IndexAlreadyExistsException iaee) {
              LOG.debug(iaee.message)
            } catch (RemoteTransportException rte) {
              LOG.debug(rte.message)
            }

            def putMapping = Requests.putMappingRequest(indexValue)
            putMapping.mappingSource = elasticMapping.toString()
            client.admin().indices().putMapping(putMapping).actionGet()
          }
        }
      }
    }
  }

  /**
   * Inject the dynamic methods in the searchable domain classes.
   * Consider that the mapping has been resolve beforehand.
   * @param domainClasses
   * @param grailsApplication
   * @param applicationContext
   * @return
   */
  static injectDynamicMethods(domainClasses, grailsApplication, applicationContext){
    def elasticSearchService = applicationContext.getBean("elasticSearchService")
    def elasticSearchContextHolder = applicationContext.getBean(ElasticSearchContextHolder)

    for (GrailsDomainClass domain in grailsApplication.domainClasses) {
      if (domain.getPropertyValue("searchable")) {
        def domainCopy = domain
        // Only inject the search method if the domain is mapped as "root"
        if (elasticSearchContextHolder.getMappingContext(domainCopy)?.classMapping?.root) {
          domain.metaClass.static.search = { String q, Map params = [indices: domainCopy.packageName ?: domainCopy.propertyName, type: domainCopy.propertyName] ->
            elasticSearchService.search(q, params)
          }
        }
      }
    }
  }
}

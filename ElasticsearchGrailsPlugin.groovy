import static org.grails.plugins.elasticsearch.ElasticSearchHelper.*
import org.codehaus.groovy.grails.commons.GrailsDomainClass;
import static org.elasticsearch.search.builder.SearchSourceBuilder.*
import static org.elasticsearch.client.Requests.*
import static org.elasticsearch.index.query.xcontent.QueryBuilders.*
import org.elasticsearch.action.search.SearchType
import org.grails.plugins.elasticsearch.ElasticSearchHelper
import org.elasticsearch.client.Client
import org.elasticsearch.client.Requests
import org.elasticsearch.transport.RemoteTransportException
import org.elasticsearch.indices.IndexAlreadyExistsException
import org.grails.plugins.elasticsearch.mapping.ElasticSearchMappingFactory
import org.grails.plugins.elasticsearch.mapping.ClosureSearchableDomainClassMapper
import org.grails.plugins.elasticsearch.conversion.CustomEditorRegistar
import org.grails.plugins.elasticsearch.ElasticSearchContextHolder
import org.grails.plugins.elasticsearch.conversion.DomainInstancesRebuilder
import org.grails.plugins.elasticsearch.ClientNodeFactoryBean
import org.codehaus.groovy.grails.orm.hibernate.HibernateEventListeners
import org.grails.plugins.elasticsearch.AuditEventListener
import org.grails.plugins.elasticsearch.JSONDomainFactory

class ElasticsearchGrailsPlugin {
  // the plugin version
  def version = "0.1"
  // the version or versions of Grails the plugin is designed for
  def grailsVersion = "1.3.4 > *"
  // the other plugins this plugin depends on
  def dependsOn = [services: "1.3 > *"]
  def loadAfter = ['services']
  // resources that are excluded from plugin packaging
  def pluginExcludes = [
          "grails-app/views/error.gsp"
  ]

  // TODO Fill in these fields
  def author = "Manuarii Stein"
  def authorEmail = "mstein@doc4web.com"
  def title = "ElasticSearch Plugin"
  def description = '''\\
Integrates ElasticSearch with Grails, allowing to index domain instances or raw data.
Based on Graeme Rocher spike.
'''

  // URL to the plugin's documentation
  def documentation = "http://grails.org/plugin/elasticsearch"

  def doWithWebDescriptor = { xml ->
    // TODO Implement additions to web.xml (optional), this event occurs before
  }

  def doWithSpring = {
    def esConfig = application.config.containsKey("elasticSearch") ? application.config.elasticSearch : null
    if(!esConfig) {
      throw new Exception('Elastic Search configuration not found.')
    }

    elasticSearchHelper(ElasticSearchHelper) {
      elasticSearchNode = ref("elasticSearchNode")
    }
    elasticSearchContextHolder(ElasticSearchContextHolder) {
      config = esConfig
    }
    elasticSearchNode(ClientNodeFactoryBean) {
      elasticSearchContextHolder = ref("elasticSearchContextHolder")
    }
    domainInstancesRebuilder(DomainInstancesRebuilder) {
      elasticSearchContextHolder = ref("elasticSearchContextHolder")
      grailsApplication = ref("grailsApplication")
    }
    customEditorRegistrar(CustomEditorRegistar)
    jsonDomainFactory(JSONDomainFactory) {
      elasticSearchContextHolder = ref("elasticSearchContextHolder")
    }
    auditListener(AuditEventListener)
    hibernateEventListeners(HibernateEventListeners) {
      listenerMap = ['post-insert': auditListener,
              'post-update': auditListener,
              'pre-delete': auditListener
      ]
    }
  }

  def onShutdown = { event ->
    event.ctx.getBean("elasticSearchNode").stop()
    event.ctx.getBean("elasticSearchNode").close()
  }

  def doWithDynamicMethods = { ctx ->
    def helper = ctx.getBean(ElasticSearchHelper)
    def domainInstancesRebuilder = ctx.getBean(DomainInstancesRebuilder)

    for (GrailsDomainClass domain in application.domainClasses) {
      if (domain.getPropertyValue("searchable")) {
        def domainCopy = domain
        domain.metaClass.static.search = { String q, Map params = [from: 0, size: 60, explain: true] ->
          helper.withElasticSearch { client ->
            try {
              def response = client.search(
                      searchRequest(domainCopy.packageName ?: domainCopy.propertyName)
                              .searchType(SearchType.DFS_QUERY_THEN_FETCH)
                              .types(domainCopy.propertyName)
                              .source(searchSource().query(queryString(q))
                              .from(params.from ?: 0)
                              .size(params.size ?: 60)
                              .explain(params.containsKey('explain') ? params.explain : true))

              ).actionGet()
              def searchHits = response.hits()
              def result = [:]
              result.total = searchHits.totalHits()

              println "Found ${result.total ?: 0} result(s)."

              // Convert the hits back to their initial type
              result.searchResults = domainInstancesRebuilder.buildResults(domainCopy, searchHits.hits())

              return result
            } catch (e) {
              e.printStackTrace()
              return [searchResult: [], total: 0]
            }
          }
        }
      }
    }
  }

  def doWithApplicationContext = { applicationContext ->
    // Implement post initialization spring config (optional)

    // Define the custom ElasticSearch mapping for searchable domain classes
    // This will eventually be done in the ElasticsearchGrailsPlugin
    def helper = applicationContext.getBean(ElasticSearchHelper)
    def elasticSearchContextHolder = applicationContext.getBean(ElasticSearchContextHolder)

    application.domainClasses.each { GrailsDomainClass domainClass ->
      if (domainClass.hasProperty('searchable') && domainClass.getPropertyValue('searchable')) {
        def indexValue = domainClass.packageName ?: domainClass.propertyName
        println "Custom mapping for searchable detected in [${domainClass.getPropertyName()}] class, resolving the closure..."
        def mappedProperties = (new ClosureSearchableDomainClassMapper(domainClass)).getPropertyMappings(domainClass, applicationContext.domainClasses as List, domainClass.getPropertyValue('searchable'))
        elasticSearchContextHolder.addMappingContext(domainClass, mappedProperties)
        def elasticMapping = ElasticSearchMappingFactory.getElasticMapping(domainClass, mappedProperties)
        println elasticMapping.toString()

        helper.withElasticSearch { Client client ->
          try {
            client.admin().indices().prepareCreate(indexValue).execute().actionGet()
            // If the index already exists, ignore the exception
          } catch (IndexAlreadyExistsException iaee) {
          } catch (RemoteTransportException rte) {}

          def putMapping = Requests.putMappingRequest(indexValue)
          putMapping.mappingSource = elasticMapping.toString()
          client.admin().indices().putMapping(putMapping).actionGet()

        }
      }
    }
  }

  def onChange = { event ->
    // TODO Implement code that is executed when any artefact that this plugin is
    // watching is modified and reloaded. The event contains: event.source,
    // event.application, event.manager, event.ctx, and event.plugin.
  }

  def onConfigChange = { event ->
    // TODO Implement code that is executed when the project configuration changes.
    // The event is the same as for 'onChange'.
  }
}
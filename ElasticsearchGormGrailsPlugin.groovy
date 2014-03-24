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


import grails.util.Environment
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.plugins.GrailsPluginManager
import org.grails.plugins.elasticsearch.AuditEventListener
import org.grails.plugins.elasticsearch.ClientNodeFactoryBean
import org.grails.plugins.elasticsearch.ElasticSearchContextHolder
import org.grails.plugins.elasticsearch.ElasticSearchHelper
import org.grails.plugins.elasticsearch.conversion.CustomEditorRegistrar
import org.grails.plugins.elasticsearch.conversion.JSONDomainFactory
import org.grails.plugins.elasticsearch.conversion.unmarshall.DomainClassUnmarshaller
import org.grails.plugins.elasticsearch.index.IndexRequestQueue
import org.grails.plugins.elasticsearch.mapping.SearchableClassMappingConfigurator
import org.grails.plugins.elasticsearch.unwrap.DomainClassUnWrapperChain
import org.grails.plugins.elasticsearch.unwrap.HibernateProxyUnWrapper
import org.grails.plugins.elasticsearch.util.DomainDynamicMethodsUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ElasticsearchGormGrailsPlugin {

    private static final Logger LOG = LoggerFactory.getLogger(this)

    def version = '0.0.2.x-SNAPSHOT'
    def grailsVersion = '2.1.0 > *'

    def loadAfter = ['services']

    def pluginExcludes = [
            'grails-app/controllers/test/**',
            'grails-app/services/test/**',
            'grails-app/views/elasticSearch/index.gsp',
            'grails-app/domain/test/**',
            'grails-app/utils/test/**',
            'test/**',
            'src/docs/**'
    ]

    def license = 'APACHE'

    def organization = [name: '10ne.org', url: 'http://www.10ne.org/']

    def developers = [
            [name: 'Noam Y. Tenne', email: 'noam@10ne.org']
    ]

    def issueManagement = [system: 'github', url: 'https://github.com/noamt/elasticsearch-gorm-plugin/issues']

    def scm = [url: 'https://github.com/noamt/elasticsearch-gorm-plugin']

    def author = 'Noam Y. Tenne'
    def authorEmail = 'noam@10ne.org'
    def title = 'ElasticSearch GORM Plugin'
    def description = """An alternative Elasticsearch plugin for Grails. Based on, but unlike the original, this implementation aims to be DB agnostic."""
    def documentation = 'http://noamt.github.io/elasticsearch-gorm-plugin'

    def doWithSpring = {
        def esConfig = getConfiguration(application)
        elasticSearchContextHolder(ElasticSearchContextHolder) {
            config = esConfig
        }
        elasticSearchHelper(ElasticSearchHelper) {
            elasticSearchClient = ref('elasticSearchClient')
        }
        elasticSearchClient(ClientNodeFactoryBean) { bean ->
            elasticSearchContextHolder = ref('elasticSearchContextHolder')
            bean.destroyMethod = 'shutdown'
        }
        indexRequestQueue(IndexRequestQueue) {
            elasticSearchContextHolder = ref('elasticSearchContextHolder')
            elasticSearchClient = ref('elasticSearchClient')
            jsonDomainFactory = ref('jsonDomainFactory')
            persistenceInterceptor = ref('persistenceInterceptor')
        }
        searchableClassMappingConfigurator(SearchableClassMappingConfigurator) { bean ->
            elasticSearchContext = ref('elasticSearchContextHolder')
            grailsApplication = ref('grailsApplication')
            elasticSearchClient = ref('elasticSearchClient')
            config = esConfig

            bean.initMethod = 'configureAndInstallMappings'
        }
        domainInstancesRebuilder(DomainClassUnmarshaller) {
            elasticSearchContextHolder = ref('elasticSearchContextHolder')
            elasticSearchClient = ref('elasticSearchClient')
            grailsApplication = ref('grailsApplication')
        }
        customEditorRegistrar(CustomEditorRegistrar) {
            grailsApplication = ref('grailsApplication')
        }

        def pluginManager = application.parentContext.getBean(GrailsPluginManager.BEAN_NAME)
        if (((GrailsPluginManager) pluginManager).hasGrailsPlugin('hibernate')) {
            hibernateProxyUnWrapper(HibernateProxyUnWrapper)
        }

        domainClassUnWrapperChain(DomainClassUnWrapperChain)

        jsonDomainFactory(JSONDomainFactory) {
            elasticSearchContextHolder = ref('elasticSearchContextHolder')
            grailsApplication = ref('grailsApplication')
            domainClassUnWrapperChain = ref('domainClassUnWrapperChain')
        }
        if (!esConfig.disableAutoIndex) {
            if (!esConfig.datastoreImpl) {
                throw new Exception('No datastore implementation specified')
            }
            auditListener(AuditEventListener, ref(esConfig.datastoreImpl)) {
                elasticSearchContextHolder = ref('elasticSearchContextHolder')
                indexRequestQueue = ref('indexRequestQueue')
            }
        }
    }

    def doWithDynamicMethods = { ctx ->
        // Define the custom ElasticSearch mapping for searchable domain classes
        DomainDynamicMethodsUtils.injectDynamicMethods(application.domainClasses, application, ctx)
    }
    // Get a configuration instance
    private getConfiguration(GrailsApplication application) {
        def config = application.config
        // try to load it from class file and merge into GrailsApplication#config
        // Config.groovy properties override the default one
        try {
            Class dataSourceClass = application.getClassLoader().loadClass('DefaultElasticSearch')
            ConfigSlurper configSlurper = new ConfigSlurper(Environment.current.name)
            Map binding = [:]
            binding.userHome = System.properties['user.home']
            binding.grailsEnv = application.metadata['grails.env']
            binding.appName = application.metadata['app.name']
            binding.appVersion = application.metadata['app.version']
            configSlurper.binding = binding

            ConfigObject defaultConfig = configSlurper.parse(dataSourceClass)

            ConfigObject newElasticSearchConfig = new ConfigObject()
            newElasticSearchConfig.putAll(defaultConfig.elasticSearch.merge(config.elasticSearch))

            config.elasticSearch = newElasticSearchConfig
            application.configChanged()
            return config.elasticSearch
        } catch (ClassNotFoundException e) {
            LOG.debug("ElasticSearch default configuration file not found: ${e.message}")
        }
        // Here the default configuration file was not found, so we
        // try to get it from GrailsApplication#config and add some mandatory default values
        if (config.containsKey('elasticSearch')) {
            if (!config.elasticSearch.date?.formats) {
                config.elasticSearch.date.formats = ['yyyy-MM-dd\'T\'HH:mm:ss\'Z\'']
            }
            if (config.elasticSearch.unmarshallComponents == [:]) {
                config.elasticSearch.unmarshallComponents = true
            }
            application.configChanged()
            return config.elasticSearch
        }

        // No config found, add some default and obligatory properties
        config.elasticSearch.date.formats = ['yyyy-MM-dd\'T\'HH:mm:ss\'Z\'']
        config.elasticSearch.unmarshallComponents = true
        application.configChanged()
        return config
    }
}
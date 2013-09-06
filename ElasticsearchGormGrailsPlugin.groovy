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
import org.apache.log4j.Logger
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.grails.plugins.elasticsearch.AuditEventListener
import org.grails.plugins.elasticsearch.ClientNodeFactoryBean
import org.grails.plugins.elasticsearch.ElasticSearchContextHolder
import org.grails.plugins.elasticsearch.ElasticSearchHelper
import org.grails.plugins.elasticsearch.conversion.CustomEditorRegistar
import org.grails.plugins.elasticsearch.conversion.JSONDomainFactory
import org.grails.plugins.elasticsearch.conversion.unmarshall.DomainClassUnmarshaller
import org.grails.plugins.elasticsearch.index.IndexRequestQueue
import org.grails.plugins.elasticsearch.mapping.SearchableClassMappingConfigurator
import org.grails.plugins.elasticsearch.util.DomainDynamicMethodsUtils

class ElasticsearchGormGrailsPlugin {

    static LOG = Logger.getLogger('org.grails.plugins.elasticsearch.ElasticsearchGormGrailsPlugin')

    // the plugin version
    def version = '0.0.1-SNAPSHOT'
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = '2.2.0 > *'

    def loadAfter = ['services']

    // resources that are excluded from plugin packaging
    def pluginExcludes = [
            'grails-app/views/error.gsp',
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
            [name: 'Manuarii Stein', email: 'mstein@doc4web.com'],
            [name: 'Stéphane Maldini', email: 'smaldini@doc4web.com']
    ]

    def issueManagement = [system: 'github', url: 'https://github.com/noamt/elasticsearch-gorm-plugin/issues']

    def scm = [url: 'https://github.com/noamt/elasticsearch-gorm-plugin']

    def author = 'Noam Y. Tenne'
    def authorEmail = 'noam@10ne.org'
    def title = 'Elastic Search Plugin'
    def description = """\
[Elastic Search|http://www.elasticsearch.com] is a distributed, RESTful service for full text search. This plugin provides a Grails-friendly API for the service based on the tremendously successful [Searchable plugin|/plugin/searchable]. It even provides an embedded version of the service for easy testing and development."""

    // URL to the plugin's documentation
    def documentation = 'http://smaldini.github.com/elasticsearch-grails-plugin/docs/guide/index.html'

    def doWithWebDescriptor = { xml ->
        // TODO Implement additions to web.xml (optional), this event occurs before
    }

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
        customEditorRegistrar(CustomEditorRegistar) {
            grailsApplication = ref('grailsApplication')
        }
        jsonDomainFactory(JSONDomainFactory) {
            elasticSearchContextHolder = ref('elasticSearchContextHolder')
            grailsApplication = ref('grailsApplication')
        }
        if (!esConfig.disableAutoIndex) {
            auditListener(AuditEventListener, ref(esConfig.datastoreImpl)) {
                elasticSearchContextHolder = ref('elasticSearchContextHolder')
                indexRequestQueue = ref('indexRequestQueue')
            }
        }
    }

    def onShutdown = { event -> }

    def doWithDynamicMethods = { ctx ->
        // Define the custom ElasticSearch mapping for searchable domain classes
        DomainDynamicMethodsUtils.injectDynamicMethods(application.domainClasses, application, ctx)
    }

    def doWithApplicationContext = { applicationContext -> }

    def onConfigChange = { event -> }

    // Get a configuration instance
    private getConfiguration(GrailsApplication application) {
        def config = application.config
        // try to load it from class file and merge into GrailsApplication#config
        // Config.groovy properties override the default one
        try {
            Class dataSourceClass = application.getClassLoader().loadClass('DefaultElasticSearch')
            ConfigSlurper configSlurper = new ConfigSlurper(Environment.current.name)
            Map binding = new HashMap()
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

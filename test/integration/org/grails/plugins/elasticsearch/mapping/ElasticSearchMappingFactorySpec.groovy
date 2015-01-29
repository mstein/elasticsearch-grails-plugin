package org.grails.plugins.elasticsearch.mapping

import grails.test.spock.IntegrationSpec
import spock.lang.Shared
import spock.lang.Unroll
import test.Building
import test.Person
import test.Product
import test.mapping.migration.Catalog
import test.transients.Anagram
import test.transients.Palette

/**
 * Created by @marcos-carceles on 28/01/15.
 */
class ElasticSearchMappingFactorySpec extends IntegrationSpec {

    @Shared def grailsApplication
    @Shared def searchableClassMappingConfigurator
    def elasticSearchContextHolder

    void setupSpec() {
        grailsApplication.config.elasticSearch.includeTransients = true
        searchableClassMappingConfigurator.configureAndInstallMappings()
    }

    void cleanupSpec() {
        grailsApplication.config.elasticSearch.includeTransients = false
        searchableClassMappingConfigurator.configureAndInstallMappings()
    }


    @Unroll('#clazz / #property is mapped as #expectedType')
    void "calculates the correct ElasticSearch types"() {
        given:
        def scm = elasticSearchContextHolder.getMappingContextByType(clazz)

        when:
        Map mapping = ElasticSearchMappingFactory.getElasticMapping(scm)

        then:
        mapping[clazz.simpleName.toLowerCase()]['properties'][property].type == expectedType

        where:
        clazz    | property          || expectedType

        Building | 'name'            || 'string'
        Building | 'date'            || 'date'
        Building | 'location'        || 'geo_point'

        Product  | 'price'           || 'float'
        Product  | 'json'            || 'object'

        Catalog  | 'pages'           || 'object'

        Person   | 'fullName'        || 'string'
        Person   | 'nickNames'       || 'string'

        Palette  | 'colors'          || 'string'
        Palette  | 'complementaries' || 'string'

        Anagram | 'length'           || 'integer'
        Anagram | 'palindrome'       || 'boolean'
    }
}
//['string', 'integer', 'long', 'float', 'double', 'boolean', 'null', 'date']
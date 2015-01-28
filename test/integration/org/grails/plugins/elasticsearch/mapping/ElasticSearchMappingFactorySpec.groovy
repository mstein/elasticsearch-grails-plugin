package org.grails.plugins.elasticsearch.mapping

import grails.test.spock.IntegrationSpec
import org.codehaus.groovy.grails.commons.GrailsDomainClass
import spock.lang.Unroll
import test.Building
import test.Person
import test.Product
import test.mapping.migration.Catalog
import test.transients.Palette

/**
 * Created by @marcos-carceles on 28/01/15.
 */
class ElasticSearchMappingFactorySpec extends IntegrationSpec {

    def elasticSearchContextHolder

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

        //Person   | 'fullName'        || 'string'
        Person   | 'nickNames'       || 'string'

        Palette  | 'complementaries' || 'string'
    }
}

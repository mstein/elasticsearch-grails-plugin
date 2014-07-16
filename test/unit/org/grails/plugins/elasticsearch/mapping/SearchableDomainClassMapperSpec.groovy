package org.grails.plugins.elasticsearch.mapping

import grails.test.mixin.Mock
import org.codehaus.groovy.grails.commons.DefaultGrailsDomainClass
import org.codehaus.groovy.grails.commons.GrailsApplication
import spock.lang.Specification
import test.Building
import test.Product

@Mock(Product)
class SearchableDomainClassMapperSpec extends Specification {

    void 'a domain class with mapping geoPoint: true is mapped as a geo_point'() {
        def config = [:] as ConfigObject
        def grailsApplication = [:] as GrailsApplication

        given: 'a mapper for Building'

        def clazz = new DefaultGrailsDomainClass(Building)
        SearchableDomainClassMapper mapper = new SearchableDomainClassMapper(grailsApplication, clazz, config)

        when: 'the mapping is built'
        def classMapping = mapper.buildClassMapping()

        then: 'the location is mapped as a geoPoint'
        classMapping.domainClass == clazz
        classMapping.elasticTypeName == 'building'
        def locationMapping = classMapping.propertiesMapping.find { it.propertyName == 'location' }
        locationMapping.isGeoPoint()
    }

    void 'the correct mapping is passed to the ES server'() {
        def config = [:] as ConfigObject
        def grailsApplication = [:] as GrailsApplication

        given: 'a mapper for Building'

        def clazz = new DefaultGrailsDomainClass(Building)
        SearchableDomainClassMapper mapper = new SearchableDomainClassMapper(grailsApplication, clazz, config)
        def classMapping = mapper.buildClassMapping()
        def mapping = ElasticSearchMappingFactory.getElasticMapping(classMapping)
        mapping == [
                building: [
                        properties: [
                                location: [
                                        type          : 'geo_point',
                                        include_in_all: true
                                ]
                        ]
                ]
        ]
    }

    void 'a mapping can be built from a domain class'() {
        def config = [:] as ConfigObject
        def grailsApplication = [:] as GrailsApplication

        given: 'a mapper for a domain class'
        SearchableDomainClassMapper mapper = new SearchableDomainClassMapper(grailsApplication, new DefaultGrailsDomainClass(Product), config)

        expect: 'a mapping can be built from this domain class'
        mapper.buildClassMapping()
    }

    void 'a mapping is aliased'() {
        def config = [:] as ConfigObject
        def grailsApplication = [:] as GrailsApplication

        given: 'a mapper for Building'

        def clazz = new DefaultGrailsDomainClass(Building)
        SearchableDomainClassMapper mapper = new SearchableDomainClassMapper(grailsApplication, clazz, config)

        when: 'the mapping is built'
        def classMapping = mapper.buildClassMapping()

        then: 'the date is aliased'
        classMapping.domainClass == clazz
        classMapping.elasticTypeName == 'building'
        def aliasMapping = classMapping.propertiesMapping.find { it.propertyName == 'date' }
        aliasMapping.isAlias()
    }

    void 'can get the mapped alias'() {
        def config = [:] as ConfigObject
        def grailsApplication = [:] as GrailsApplication

        given: 'a mapper for Building'

        def clazz = new DefaultGrailsDomainClass(Building)
        SearchableDomainClassMapper mapper = new SearchableDomainClassMapper(grailsApplication, clazz, config)

        when: 'the mapping is built'
        def classMapping = mapper.buildClassMapping()

        then: 'the date is aliased'
        classMapping.domainClass == clazz
        classMapping.elasticTypeName == 'building'
        def aliasMapping = classMapping.propertiesMapping.find { it.propertyName == 'date' }
        aliasMapping.getAlias() == "@timestamp"
    }
}

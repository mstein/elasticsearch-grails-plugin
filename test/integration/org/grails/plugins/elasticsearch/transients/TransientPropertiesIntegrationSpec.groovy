package org.grails.plugins.elasticsearch.transients

import grails.test.spock.IntegrationSpec
import org.grails.plugins.elasticsearch.ElasticSearchAdminService
import org.grails.plugins.elasticsearch.ElasticSearchService
import org.grails.plugins.elasticsearch.mapping.SearchableClassMappingConfigurator
import test.transients.Anagram
import test.transients.Calculation
import test.transients.Color
import test.transients.Palette

/**
 * Created by @marcos-carceles on 29/01/15.
 */
class TransientPropertiesIntegrationSpec extends IntegrationSpec {

    def grailsApplication
    ElasticSearchService elasticSearchService
    ElasticSearchAdminService elasticSearchAdminService
    SearchableClassMappingConfigurator searchableClassMappingConfigurator

    void 'when includeTransients config is false only properties explictly included in only are indexed and searchable'() {
        expect:
        grailsApplication.config.elasticSearch.includeTransients == false

        when: "Indexing some instances"
        def toIndex = []
        toIndex << new Anagram(original: "unbelievable").save(flush: true)
        toIndex << new Calculation(a: 21, b: 3).save(flush: true)
        toIndex << new Palette(author: "Picasso", colors: [Color.red, Color.blue]).save(flush: true)
        assert toIndex[2].description == "Picasso likes to paint with [red, blue]"
        elasticSearchService.index(toIndex)
        elasticSearchAdminService.refresh()

        and: "searching for explictly indexed transients"
        def results = Palette.search("cyan")

        then: "we find results when searching for transients explicitly mapped with 'only'"
        results.total == 1

        and: "transients use data stored on ElasticSearch"
        results.searchResults.first().complementaries == ['cyan', 'yellow']
        results.searchResults.first().description == null //as author is not stored in ElasticSearch

        and: "we don't find any other transients"
        Anagram.search("elbaveilebnu").total == 0
        Calculation.search("24").total == 0
        Calculation.search("63").total == 0
    }

    void 'when includeTransients config is true all non excluded transient properties are indexed and searchable'() {
        given: "the configuration says to always include transients"
        grailsApplication.config.elasticSearch.includeTransients = true
        elasticSearchAdminService.deleteIndex(Anagram, Calculation, Palette)
        searchableClassMappingConfigurator.configureAndInstallMappings()

        when: "Indexing some instances"
        def toIndex = []
        toIndex << new Anagram(original: "unbelievable").save(flush: true)
        toIndex << new Calculation(a: 21, b: 3).save(flush: true)
        toIndex << new Palette(author: "Picasso", colors: [Color.red, Color.blue]).save(flush: true)
        assert toIndex[2].description == "Picasso likes to paint with [red, blue]"
        elasticSearchService.index(toIndex)
        elasticSearchAdminService.refresh()

        then: "We can search using any transient"
        Palette.search("cyan").total == 1
        Anagram.search("elbaveilebnu").total == 1
        Calculation.search("24").total == 1
        Calculation.search("63").total == 1
        Calculation.search("7").total == 0 //because division is not indexed

        and: "transients on results use data stored on ElasticSearch"
        Calculation calc = Calculation.search("24").searchResults.first()
        calc.multiplication == 63 //as multiplication is stored in ElasticSearch
        calc.addition == 0 //as properties a and b are not stored in ElasticSearch

        when: "domain objects are fetched"
        calc = Calculation.get(calc.id)

        then: "all propertie are available"
        calc.addition == 24

        cleanup:
        grailsApplication.config.elasticSearch.includeTransients = false
        searchableClassMappingConfigurator.configureAndInstallMappings()
    }

}

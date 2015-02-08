package org.grails.plugins.elasticsearch

import grails.test.spock.IntegrationSpec
import spock.lang.Shared
import test.Photo

import org.elasticsearch.index.query.FilterBuilder
import org.elasticsearch.index.query.FilterBuilders
import org.elasticsearch.index.query.QueryBuilder
import org.elasticsearch.index.query.QueryBuilders

class DynamicMethodsIntegrationSpec extends IntegrationSpec {

    def elasticSearchAdminService
    def elasticSearchService
    @Shared captains = []

    def setupSpec() {
        captains << new Photo(name:"Captain Kirk",    url:"http://www.nicenicejpg.com/100").save(failOnError: true)
        captains << new Photo(name:"Captain Picard",  url:"http://www.nicenicejpg.com/200").save(failOnError: true)
        captains << new Photo(name:"Captain Sisko",   url:"http://www.nicenicejpg.com/300").save(failOnError: true)
        captains << new Photo(name:"Captain Janeway", url:"http://www.nicenicejpg.com/400").save(failOnError: true)
        captains << new Photo(name:"Captain Archer",  url:"http://www.nicenicejpg.com/500").save(failOnError: true)
    }

    def cleanupSpec() {
        captains.each { Photo captain ->
            captain.delete()
        }
    }

    def "can search using Dynamic Methods"() {
        given:
        elasticSearchAdminService.refresh()
        expect:
        elasticSearchService.search('captain', [indices: Photo, types: Photo]).total == 5

        when:
        def results = Photo.search {
            match(name:"Captain")
        }

        then:
        results.total == 5
        results.searchResults.every { it.name =~ /Captain/ }
    }

    def "can search and filter using Dynamic Methods"() {
        given:
        elasticSearchAdminService.refresh()
        expect:
        elasticSearchService.search('Captain', [indices: Photo, types: Photo]).total == 5

        when:
        def results = Photo.search({
            match(name:"Captain")
        }, {
            term(url:"http://www.nicenicejpg.com/100")
        })

        then:
        results.total == 1
        results.searchResults[0].name == "Captain Kirk"
    }

    def "can search using a QueryBuilder and Dynamic Methods"() {
        given:
        elasticSearchAdminService.refresh()
        expect:
        elasticSearchService.search('captain', [indices: Photo, types: Photo]).total == 5

        when:
        QueryBuilder query = QueryBuilders.termQuery("url", "http://www.nicenicejpg.com/100")
        def results = Photo.search(query)

        then:
        results.total == 1
        results.searchResults[0].name == "Captain Kirk"
    }

    def "can search using a QueryBuilder, a filter and Dynamic Methods"() {
        given:
        elasticSearchAdminService.refresh()
        expect:
        elasticSearchService.search('captain', [indices: Photo, types: Photo]).total == 5

         when:
        QueryBuilder query = QueryBuilders.matchQuery("name", "Captain")
        def results = Photo.search(query,
        {
            term(url:"http://www.nicenicejpg.com/100")
        })

        then:
        results.total == 1
        results.searchResults[0].name == "Captain Kirk"
    }
	
	def "can search using a QueryBuilder, a FilterBuilder and Dynamic Methods"() {
		given:
		elasticSearchAdminService.refresh()
		expect:
		elasticSearchService.search('captain', [indices: Photo, types: Photo]).total == 5

		 when:
		QueryBuilder query = QueryBuilders.matchAllQuery()
		FilterBuilder filter = FilterBuilders.termFilter("url", "http://www.nicenicejpg.com/100")
		def results = Photo.search(query, filter)

		then:
		results.total == 1
		results.searchResults[0].name == "Captain Kirk"
	}
	
	def "can search and filter using Dynamic Methods and a FilterBuilder"() {
		given:
		elasticSearchAdminService.refresh()
		expect:
		elasticSearchService.search('Captain', [indices: Photo, types: Photo]).total == 5

		when:
		FilterBuilder filter = FilterBuilders.termFilter("url", "http://www.nicenicejpg.com/100")
		def results = Photo.search({
			match(name:"Captain")
		}, filter)

		then:
		results.total == 1
		results.searchResults[0].name == "Captain Kirk"
	}
}

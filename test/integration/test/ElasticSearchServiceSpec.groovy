package test

import grails.plugin.spock.IntegrationSpec
import org.apache.log4j.Logger

class ElasticSearchServiceSpec extends IntegrationSpec {
    def elasticSearchService
    def elasticSearchAdminService
    private static final Logger LOG = Logger.getLogger(this)

    def setup() {
        // Make sure the indices are cleaned
        println "cleaning indices"
        elasticSearchAdminService.deleteIndex()
        elasticSearchAdminService.refresh()
    }

    def "Index a domain object"() {
        given:
        def product = new Product(name: "myTestProduct")
        product.save()

        when:
        elasticSearchService.index(product)
        elasticSearchAdminService.refresh()  // Ensure the latest operations have been exposed on the ES instance

        then:
        elasticSearchService.search("myTestProduct", [indices: Product, types: Product]).total == 1
    }

    def "Unindex method delete index from ES"() {
        given:
        def product = new Product(name: "myTestProduct")
        product.save()

        when:
        elasticSearchService.index(product)
        elasticSearchAdminService.refresh()  // Ensure the latest operations have been exposed on the ES instance

        and:
        elasticSearchService.search("myTestProduct", [indices: Product, types: Product]).total == 1

        then:
        elasticSearchService.unindex(product)
        elasticSearchAdminService.refresh()

        and:
        elasticSearchService.search("myTestProduct", [indices: Product, types: Product]).total == 0
    }

    def "Indexing multiple time the same object update the corresponding ES entry"() {
        given:
        def product = new Product(name: "myTestProduct")
        product.save()

        when:
        elasticSearchService.index(product)
        elasticSearchAdminService.refresh()

        then:
        elasticSearchService.search("myTestProduct", [indices: Product, types: Product]).total == 1

        when:
        product.name = "newProductName"
        elasticSearchService.index(product)
        elasticSearchAdminService.refresh()

        then:
        elasticSearchService.search("myTestProduct", [indices: Product, types: Product]).total == 0

        and:
        def result = elasticSearchService.search(product.name, [indices: Product, types: Product])
        result.total == 1
        result.searchResults[0].name == product.name
    }
}

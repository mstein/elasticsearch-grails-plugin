package test
import grails.plugin.spock.IntegrationSpec

class ElasticSearchServiceSpec extends IntegrationSpec {
    def elasticSearchService
    def elasticSearchContextHolder

    def setup(){

    }

    def "Unindex method delete index from ES"() {
        when :
        def product = new Product(name:"myTestProduct")
        product.save()
        elasticSearchService.index(product)
        // because the indexing process is asynchronous, we wait a little to make sure the test data
        // is indeed stored on the ES instance
        sleep(500)

        then:
        elasticSearchService.search("*").total == 1
        //assert elasticSearchService.search("myTestProduct", [indices:'test', types:Product.class]).total == 1

        when:
        elasticSearchService.unindex(product)

        then:
        elasticSearchService.search("name:myTestProduct", [indices:'test', types:Product.class]).total == 0
    }
}

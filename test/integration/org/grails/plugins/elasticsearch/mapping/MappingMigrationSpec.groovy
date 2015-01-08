package org.grails.plugins.elasticsearch.mapping

import grails.test.spock.IntegrationSpec
import org.grails.plugins.elasticsearch.exception.MappingException
import org.grails.plugins.elasticsearch.util.ElasticSearchShortcuts
import test.mapping.migration.Catalog
import test.mapping.migration.Product

/**
 * Created by @marcos-carceles on 07/01/15.
 */
class MappingMigrationSpec extends IntegrationSpec {

    def grailsApplication
    def searchableClassMappingConfigurator
    def elasticSearchService
    def elasticSearchAdminService
    def elasticSearchShortcuts

    ElasticSearchShortcuts getEs() {
        elasticSearchShortcuts
    }

    def setup() {
        // Recreate a clean environment as if the app had just booted
        es.deleteMapping(catalogMapping.indexName, catalogMapping.elasticTypeName)
        es.deleteIndex(catalogMapping.indexName)
        searchableClassMappingConfigurator.configureAndInstallMappings()
    }

    /*
     * STRATEGY : none
     * case 1: Nothing exists
     * case 2: Conflict
     */

    void "An index is created when nothing exists"() {
        given: "That an index does not exist"
        es.deleteIndex catalogMapping.indexName

        and: "Alias Configuration"
        grailsApplication.config.elasticSearch.migration = [strategy: "none"]

        expect:
        !es.indexExists(catalogMapping.indexName)

        when:
        searchableClassMappingConfigurator.installMappings([catalogMapping])

        then:
        !es.aliasExists(catalogMapping.indexName)
        es.indexExists(catalogMapping.indexName)
        es.mappingExists(catalogMapping.indexName, catalogMapping.elasticTypeName)
    }

    void "when there's a conflict and no strategy is selected an exception is thrown"() {

        given: "A Conflicting Catalog mapping (with nested as opposed to inner pages)"
        createConflictingCatalogMapping()

        and: "No Migration Configuration"
        grailsApplication.config.elasticSearch.migration = [strategy: "none"]

        when:
        searchableClassMappingConfigurator.installMappings([catalogMapping])

        then:
        thrown MappingException
    }

    /*
     * STRATEGY : delete
     * case 1: Incompatible Index exists
     * case 2: Incompatible Alias exists
     */

    void "when there's a conflict and strategy is 'delete' content is deleted"() {

        given: "A Conflicting Catalog mapping (with nested as opposed to inner pages)"
        createConflictingCatalogMapping()

        and: "Delete Configuration"
        grailsApplication.config.elasticSearch.migration = [strategy: "delete"]

        and: "Existing content"
        new Catalog(company:"ACME", issue: 1).save(flush:true,failOnError: true)
        new Catalog(company:"ACME", issue: 2).save(flush:true,failOnError: true)
        elasticSearchService.index()
        elasticSearchAdminService.refresh()

        expect:
        Catalog.count() == 2
        Catalog.search("ACME").total == 2

        when: "Installing the conflicting mapping"
        searchableClassMappingConfigurator.installMappings([catalogMapping])

        then: "It succeeds"
        es.indexExists(catalogMapping.indexName)
        es.mappingExists catalogMapping.indexName, catalogMapping.elasticTypeName

        and: "Documents are lost as maping was recreated"
        Catalog.search("ACME").total == 0

        and: "No alias was created"
        !es.aliasExists(catalogMapping.indexName)

        cleanup:
        Catalog.list().each { it.delete() }
    }

    void "delete works on alias as well"() {

        given: "An alias pointing to a versioned index"
        es.deleteIndex catalogMapping.indexName
        es.createIndex catalogMapping.indexName, 0
        es.pointAliasTo catalogMapping.indexName, catalogMapping.indexName, 0
        searchableClassMappingConfigurator.configureAndInstallMappings()

        and: "A Conflicting Catalog mapping (with nested as opposed to inner pages)"
        createConflictingCatalogMapping()

        and: "Delete Configuration"
        grailsApplication.config.elasticSearch.migration = [strategy: "delete"]

        and: "Existing content"
        new Catalog(company:"ACME", issue: 1).save(flush:true,failOnError: true)
        new Catalog(company:"ACME", issue: 2).save(flush:true,failOnError: true)
        elasticSearchService.index()
        elasticSearchAdminService.refresh()

        expect:
        es.indexExists(catalogMapping.indexName)
        es.aliasExists(catalogMapping.indexName)
        es.indexExists(catalogMapping.indexName, 0)
        es.indexPointedBy(catalogMapping.indexName) == es.versionIndex(catalogMapping.indexName, 0)
        Catalog.count() == 2
        Catalog.search("ACME").total == 2

        when: "Installing the conflicting mapping"
        searchableClassMappingConfigurator.installMappings([catalogMapping])

        then: "It succeeds"
        es.indexExists(catalogMapping.indexName)
        es.mappingExists catalogMapping.indexName, catalogMapping.elasticTypeName

        and: "The alias was not modified"
        es.indexExists(catalogMapping.indexName)
        es.aliasExists(catalogMapping.indexName)
        es.indexExists(catalogMapping.indexName, 0)
        es.indexPointedBy(catalogMapping.indexName) == es.versionIndex(catalogMapping.indexName, 0)

        and: "Documents are lost as mapping was recreated"
        Catalog.search("ACME").total == 0

        cleanup:
        Catalog.list().each { it.delete() }
    }

    /*
     * STRATEGY : alias
     * case 1: Index does not exist
     * case 2: Alias exists
     * case 3: Index exists
     */

    void "With 'alias' strategy an index and an alias are created when none exist"() {
        given: "That an index does not exist"
        es.deleteIndex catalogMapping.indexName

        and: "Alias Configuration"
        grailsApplication.config.elasticSearch.migration = [strategy: "alias"]

        expect:
        !es.indexExists(catalogMapping.indexName)

        when:
        searchableClassMappingConfigurator.installMappings([catalogMapping])

        then:
        es.indexExists(catalogMapping.indexName)
        es.aliasExists(catalogMapping.indexName)
        es.indexExists(catalogMapping.indexName, 0)
        es.indexPointedBy(catalogMapping.indexName) == es.versionIndex(catalogMapping.indexName, 0)
        es.mappingExists(catalogMapping.indexName, catalogMapping.elasticTypeName)
    }

    void "With 'alias' strategy if alias exist, the next one is created"() {
        given: "A range of previously created versions"
        es.deleteIndex catalogMapping.indexName
        (0..10).each {
            es.createIndex catalogMapping.indexName, it
        }
        es.pointAliasTo catalogMapping.indexName, catalogMapping.indexName, 10
        searchableClassMappingConfigurator.configureAndInstallMappings()

        and: "Two different mapping conflicts on the same index"
        assert catalogMapping != productMapping
        assert catalogMapping.indexName == productMapping.indexName
        createConflictingCatalogMapping()
        createConflictingProductMapping()

        and: "Alias Configuration"
        grailsApplication.config.elasticSearch.migration = [strategy: "alias"]

        expect:
        es.indexExists catalogMapping.indexName
        es.aliasExists catalogMapping.indexName
        es.indexExists catalogMapping.indexName, 10
        es.indexPointedBy(catalogMapping.indexName) == es.versionIndex(catalogMapping.indexName, 10)

        when:
        searchableClassMappingConfigurator.installMappings([catalogMapping, productMapping])

        then: "A new version is created"
        es.indexExists catalogMapping.indexName
        es.aliasExists catalogMapping.indexName
        es.indexExists catalogMapping.indexName, 11

        and: "Only one version is created and not a version per conflict"
        es.indexPointedBy(catalogMapping.indexName) == es.versionIndex(catalogMapping.indexName, 11)
        !es.indexExists(catalogMapping.indexName, 12)
    }

    def _catalogMapping = null
    def _productMapping = null
    private SearchableClassMapping getMappingFor(Class clazz) {
        searchableClassMappingConfigurator.mappings().find { SearchableClassMapping it ->
            it.domainClass.clazz == clazz
        }
    }

    private SearchableClassMapping getCatalogMapping() {
        if (!_catalogMapping) {
            _catalogMapping = getMappingFor(Catalog)
        }
        _catalogMapping
    }

    private SearchableClassMapping getProductMapping() {
        if (!_productMapping) {
            _productMapping = getMappingFor(Product)
        }
        _productMapping
    }

    private void createConflictingCatalogMapping() {
        //Delete existing Mapping
        es.deleteMapping catalogMapping.indexName, catalogMapping.elasticTypeName
        //Create conflicting Mapping
        SearchableClassPropertyMapping pagesMapping = catalogMapping.propertiesMapping.find {
            it.propertyName == "pages"
        }
        pagesMapping.addAttributes([component:true])
        searchableClassMappingConfigurator.installMappings([catalogMapping])
        //Restore initial state for next use
        pagesMapping.addAttributes([component:'inner'])
    }

    private void createConflictingProductMapping() {
        //Delete existing Mapping
        es.deleteMapping productMapping.indexName, productMapping.elasticTypeName
        //Create conflicting Mapping
        SearchableClassPropertyMapping supplierMapping = productMapping.propertiesMapping.find {
            it.propertyName == "supplier"
        }
        supplierMapping.addAttributes([component:true])
        searchableClassMappingConfigurator.installMappings([productMapping])
        //Restore initial state for next use
        supplierMapping.addAttributes([component:'inner'])
    }
}

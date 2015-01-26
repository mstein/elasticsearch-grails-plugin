package org.grails.plugins.elasticsearch.mapping

import grails.test.spock.IntegrationSpec
import org.grails.plugins.elasticsearch.ElasticSearchAdminService
import org.grails.plugins.elasticsearch.exception.MappingException
import test.mapping.migration.Catalog
import test.mapping.migration.Item


/**
 * Created by @marcos-carceles on 07/01/15.
 */
class MappingMigrationSpec extends IntegrationSpec {

    def grailsApplication
    def searchableClassMappingConfigurator
    def elasticSearchContextHolder
    def elasticSearchService
    def elasticSearchAdminService
    def elasticSearchBootStrapHelper

    ElasticSearchAdminService getEs() {
        elasticSearchAdminService
    }

    def setup() {
        es.getIndices().each {
            es.deleteIndex(it)
        }
        // Recreate a clean environment as if the app had just booted
        grailsApplication.config.elasticSearch.migration = [strategy: "none"]
        grailsApplication.config.elasticSearch.bulkIndexOnStartup = false
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
        !es.indexExists(catalogMapping.queryingIndex)
        !es.indexExists(catalogMapping.indexingIndex)

        when: "Installing the mappings"
        searchableClassMappingConfigurator.installMappings([catalogMapping])

        then: "The Index and mapping is created"
        es.indexExists(catalogMapping.indexName)
        es.mappingExists(catalogMapping.indexName, catalogMapping.elasticTypeName)

        and: "There are aliases for reading and writing"
        es.indexPointedBy(catalogMapping.queryingIndex) == catalogMapping.indexName
        es.indexPointedBy(catalogMapping.indexingIndex) == catalogMapping.indexName

    }

    void "Read and Write aliases are created when none exist"() {
        given: "An index without read and write aliases"
        es.deleteAlias(catalogMapping.indexingIndex)
        es.deleteAlias(catalogMapping.queryingIndex)

        expect:
        es.indexExists(catalogMapping.indexName)
        !es.aliasExists(catalogMapping.indexName)
        !es.aliasExists(catalogMapping.indexingIndex)
        !es.aliasExists(catalogMapping.queryingIndex)

        when: "Installing the mappings"
        searchableClassMappingConfigurator.installMappings([catalogMapping])

        then: "The aliases are created"
        es.indexExists(catalogMapping.indexName)
        es.indexPointedBy(catalogMapping.queryingIndex) == catalogMapping.indexName
        es.indexPointedBy(catalogMapping.indexingIndex) == catalogMapping.indexName
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
        es.indexPointedBy(catalogMapping.queryingIndex) == catalogMapping.indexName
        Catalog.count() == 2
        Catalog.search("ACME").total == 2

        when: "Installing the conflicting mapping"
        searchableClassMappingConfigurator.installMappings([catalogMapping])

        then: "It succeeds"
        es.indexExists(catalogMapping.indexName)
        es.indexPointedBy(catalogMapping.indexingIndex) == catalogMapping.indexName
        es.indexPointedBy(catalogMapping.queryingIndex) == catalogMapping.indexName
        es.mappingExists catalogMapping.indexName, catalogMapping.elasticTypeName

        and: "Documents are lost on ES as mapping was recreated"
        Catalog.count() == 2
        Catalog.search("ACME").total == 0

        and: "No alias was created"
        !es.aliasExists(catalogMapping.indexName)

        cleanup:
        Catalog.findAll().each { it.delete() }
    }

    void "delete works on alias as well"() {

        given: "An alias pointing to a versioned index"
        es.deleteIndex catalogMapping.indexName
        es.createIndex catalogMapping.indexName, 0
        es.pointAliasTo catalogMapping.indexName, catalogMapping.indexName, 0
        es.pointAliasTo catalogMapping.queryingIndex, catalogMapping.indexName
        es.pointAliasTo catalogMapping.indexingIndex, catalogMapping.indexName
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
        es.indexExists(catalogMapping.indexName, 0)
        es.indexPointedBy(catalogMapping.indexName) == es.versionIndex(catalogMapping.indexName, 0)
        es.indexPointedBy(catalogMapping.queryingIndex) == es.versionIndex(catalogMapping.indexName, 0)
        es.indexPointedBy(catalogMapping.indexingIndex) == es.versionIndex(catalogMapping.indexName, 0)
        Catalog.count() == 2
        Catalog.search("ACME").total == 2

        when: "Installing the conflicting mapping"
        searchableClassMappingConfigurator.installMappings([catalogMapping])

        then: "It succeeds"
        es.mappingExists catalogMapping.indexName, catalogMapping.elasticTypeName

        and: "The aliases were not modified"
        es.indexExists(catalogMapping.indexName, 0)
        es.indexPointedBy(catalogMapping.indexName) == es.versionIndex(catalogMapping.indexName, 0)
        es.indexPointedBy(catalogMapping.queryingIndex) == es.versionIndex(catalogMapping.indexName, 0)
        es.indexPointedBy(catalogMapping.indexingIndex) == es.versionIndex(catalogMapping.indexName, 0)

        and: "Documents are lost on ES as mapping was recreated"
        Catalog.count() == 2
        Catalog.search("ACME").total == 0

        cleanup:
        Catalog.findAll().each { it.delete() }
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
        es.indexExists(catalogMapping.indexName, 0)
        es.indexPointedBy(catalogMapping.indexName) == es.versionIndex(catalogMapping.indexName, 0)
        es.indexPointedBy(catalogMapping.queryingIndex) == es.versionIndex(catalogMapping.indexName, 0)
        es.indexPointedBy(catalogMapping.indexingIndex) == es.versionIndex(catalogMapping.indexName, 0)
        es.mappingExists(catalogMapping.indexName, catalogMapping.elasticTypeName)
    }

    void "With 'alias' strategy if alias exist, the next one is created"() {
        given: "A range of previously created versions"
        es.deleteIndex catalogMapping.indexName
        (0..10).each {
            es.createIndex catalogMapping.indexName, it
        }
        es.pointAliasTo catalogMapping.indexName, catalogMapping.indexName, 10
        es.pointAliasTo catalogMapping.queryingIndex, catalogMapping.indexName
        es.pointAliasTo catalogMapping.indexingIndex, catalogMapping.indexName
        searchableClassMappingConfigurator.configureAndInstallMappings()

        and: "Two different mapping conflicts on the same index"
        assert catalogMapping != itemMapping
        assert catalogMapping.indexName == itemMapping.indexName
        createConflictingCatalogMapping()
        createConflictingProductMapping()

        and: "Alias Configuration"
        grailsApplication.config.elasticSearch.migration = [strategy: "alias"]
        grailsApplication.config.elasticSearch.bulkIndexOnStartup = false //Content creation tested on a different test

        expect:
        es.indexExists catalogMapping.indexName, 10
        es.indexPointedBy(catalogMapping.indexName) == es.versionIndex(catalogMapping.indexName, 10)
        es.indexPointedBy(catalogMapping.queryingIndex) == es.versionIndex(catalogMapping.indexName, 10)
        es.indexPointedBy(catalogMapping.indexingIndex) == es.versionIndex(catalogMapping.indexName, 10)

        when:
        searchableClassMappingConfigurator.installMappings([catalogMapping, itemMapping])

        then: "A new version is created"
        es.indexExists catalogMapping.indexName, 11
        es.indexPointedBy(catalogMapping.indexName) == es.versionIndex(catalogMapping.indexName, 11)
        es.indexPointedBy(catalogMapping.queryingIndex) == es.versionIndex(catalogMapping.indexName, 11)
        es.indexPointedBy(catalogMapping.indexingIndex) == es.versionIndex(catalogMapping.indexName, 11)

        and: "Only one version is created and not a version per conflict"
        catalogMapping.indexName == itemMapping.indexName
        catalogMapping.queryingIndex == itemMapping.queryingIndex
        catalogMapping.indexingIndex == itemMapping.indexingIndex
        !es.indexExists(catalogMapping.indexName, 12)

        and: "Others mappings are created as well"
        es.mappingExists(itemMapping.indexName, itemMapping.elasticTypeName)
    }

    void "With 'alias' strategy if index exists, decide whether to replace with alias based on config"() {
        given: "Two different mapping conflicts on the same index"
        assert catalogMapping != itemMapping
        assert catalogMapping.indexName == itemMapping.indexName
        createConflictingCatalogMapping()
        createConflictingProductMapping()

        and: "Existing content"
        new Catalog(company:"ACME", issue: 1).save(flush:true,failOnError: true)
        new Catalog(company:"ACME", issue: 2).save(flush:true,failOnError: true)
        new Item(name:"Road Runner Ultrafast Glue").save(flush:true, failOnError: true)
        elasticSearchService.index()
        elasticSearchAdminService.refresh()

        expect:
        es.indexExists catalogMapping.indexName
        !es.aliasExists(catalogMapping.indexName)
        Catalog.count() == 2
        Catalog.search("ACME").total == 2
        Item.count() == 1
        Item.search("Glue").total == 1

        when:
        grailsApplication.config.elasticSearch.migration = [strategy: "alias", "aliasReplacesIndex" : false]
        searchableClassMappingConfigurator.installMappings([catalogMapping, itemMapping])

        then: "an exception is thrown, due to the existing index"
        thrown MappingException

        and: "no content or mappings are affected"
        es.indexExists(catalogMapping.indexName)
        !es.aliasExists(catalogMapping.indexName)
        Catalog.count() == 2
        Catalog.search("ACME").total == 2
        Item.count() == 1
        Item.search("Glue").total == 1
        es.mappingExists(catalogMapping.indexName, catalogMapping.elasticTypeName)
        es.mappingExists(itemMapping.indexName, catalogMapping.elasticTypeName)

        when:
        grailsApplication.config.elasticSearch.migration = [strategy: "alias", "aliasReplacesIndex" : true]
        grailsApplication.config.elasticSearch.bulkIndexOnStartup = false //On the other cases content is recreated
        searchableClassMappingConfigurator.installMappings([catalogMapping])

        then: "Alias replaces the index"
        es.indexPointedBy(catalogMapping.indexingIndex) == es.versionIndex(catalogMapping.indexName, 0)
        es.indexPointedBy(catalogMapping.queryingIndex) == es.versionIndex(catalogMapping.indexName, 0)
        es.indexPointedBy(catalogMapping.indexName) == es.versionIndex(catalogMapping.indexName, 0)
        es.mappingExists(catalogMapping.indexName, catalogMapping.elasticTypeName)

        and: "Content is lost, as the index is regenerated"
        Catalog.count() == 2
        Catalog.search("ACME").total == 0
        Item.count() == 1
        Item.search("Glue").total == 0

        and: "All mappings are recreated"
        es.mappingExists(catalogMapping.indexName, catalogMapping.elasticTypeName)
        es.mappingExists(itemMapping.indexName, catalogMapping.elasticTypeName)

        cleanup:
        Catalog.findAll().each { it.delete() }
        Item.findAll().each { it.delete() }
    }

    /*
     * Tests for bulkIndexOnStartup = "deleted"
     * Zero Downtime for Alias to Alias
     * Minimise Downtime for Index to Alias
     */
    void "Alias -> Alias : If configuration says to recreate the content, there is zero downtime"() {

        given: "An existing Alias"
        es.deleteIndex catalogMapping.indexName
        es.createIndex catalogMapping.indexName, 0
        es.pointAliasTo catalogMapping.indexName, catalogMapping.indexName, 0
        es.pointAliasTo catalogMapping.queryingIndex, catalogMapping.indexName
        es.pointAliasTo catalogMapping.indexingIndex, catalogMapping.indexName
        searchableClassMappingConfigurator.configureAndInstallMappings()

        and: "A mapping conflict"
        createConflictingCatalogMapping()

        and: "Existing content"
        new Catalog(company:"ACME", issue: 1).save(flush:true,failOnError: true)
        new Catalog(company:"ACME", issue: 2).save(flush:true,failOnError: true)
        new Item(name:"Road Runner Ultrafast Glue").save(flush:true, failOnError: true)
        elasticSearchService.index()
        elasticSearchAdminService.refresh()

        expect:
        Catalog.count() == 2
        Catalog.search("ACME").total == 2
        Item.count() == 1
        Item.search("Glue").total == 1

        when: "The mapping is installed and migrations happens"
        grailsApplication.config.elasticSearch.migration = [strategy: "alias"]
        grailsApplication.config.elasticSearch.bulkIndexOnStartup = "deleted"
        searchableClassMappingConfigurator.installMappings([catalogMapping, itemMapping])

        then: "Temporarily, while indexing occurs, indexing happens on the new index, while querying on the old one"
        es.indexPointedBy(catalogMapping.queryingIndex) == es.versionIndex(catalogMapping.indexName, 0)
        es.indexPointedBy(catalogMapping.indexingIndex) == es.versionIndex(catalogMapping.indexName, 1)

        then: "All aliases, indexes and mappings exist"
        es.indexPointedBy(catalogMapping.indexName) == es.versionIndex(catalogMapping.indexName, 1)
        es.mappingExists(catalogMapping.queryingIndex, catalogMapping.elasticTypeName)
        es.mappingExists(itemMapping.queryingIndex, itemMapping.elasticTypeName)
        es.mappingExists(catalogMapping.indexingIndex, catalogMapping.elasticTypeName)
        es.mappingExists(itemMapping.indexingIndex, itemMapping.elasticTypeName)

        and: "Content isn't lost as it keeps pointing to the old index"
        Catalog.search("ACME").total == 2
        Item.search("Glue").total == 1

        when: "Bootstrap runs"
        elasticSearchBootStrapHelper.bulkIndexOnStartup()
        and:
        elasticSearchAdminService.refresh()

        then: "All aliases now point to the new index"
        es.indexPointedBy(catalogMapping.indexName) == es.versionIndex(catalogMapping.indexName, 1)
        es.indexPointedBy(catalogMapping.queryingIndex) == es.versionIndex(catalogMapping.indexName, 1)
        es.indexPointedBy(catalogMapping.indexingIndex) == es.versionIndex(catalogMapping.indexName, 1)

        and: "Content is still found"
        Catalog.search("ACME").total == 2
        Item.search("Glue").total == 1

        cleanup:
        Catalog.findAll().each { it.delete() }
        Item.findAll().each { it.delete() }

    }

    private SearchableClassMapping getCatalogMapping() {
        elasticSearchContextHolder.getMappingContextByType(Catalog)
    }

    private SearchableClassPropertyMapping getCatalogPagesMapping() {
        catalogMapping.propertiesMapping.find {
            it.propertyName == "pages"
        }
    }

    private SearchableClassMapping getItemMapping() {
        elasticSearchContextHolder.getMappingContextByType(Item)
    }

    private SearchableClassPropertyMapping getItemSupplierMapping() {
        itemMapping.propertiesMapping.find {
            it.propertyName == "supplier"
        }
    }

    private void createConflictingCatalogMapping() {
        //Delete existing Mapping
        es.deleteMapping catalogMapping.indexName, catalogMapping.elasticTypeName
        //Create conflicting Mapping
        catalogPagesMapping.addAttributes([component:true])
        searchableClassMappingConfigurator.installMappings([catalogMapping])
        //Restore initial state for next use
        catalogPagesMapping.addAttributes([component:'inner'])
    }

    private void createConflictingProductMapping() {
        //Delete existing Mapping
        es.deleteMapping itemMapping.indexName, itemMapping.elasticTypeName
        //Create conflicting Mapping
        itemSupplierMapping.addAttributes([component:true])
        searchableClassMappingConfigurator.installMappings([itemMapping])
        //Restore initial state for next use
        itemSupplierMapping.addAttributes([component:'inner'])
    }
}

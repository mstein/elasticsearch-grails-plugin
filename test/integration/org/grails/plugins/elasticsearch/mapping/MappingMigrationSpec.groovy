package org.grails.plugins.elasticsearch.mapping

import grails.test.spock.IntegrationSpec
import org.grails.plugins.elasticsearch.exception.MappingException
import org.grails.plugins.elasticsearch.util.ElasticSearchShortcuts
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
    def elasticSearchShortcuts
    def elasticSearchBooStrapHelper

    ElasticSearchShortcuts getEs() {
        elasticSearchShortcuts
    }

    def setup() {
        // Recreate a clean environment as if the app had just booted
        es.deleteMapping(catalogMapping.queryingIndex, catalogMapping.elasticTypeName)
        es.deleteIndex(catalogMapping.queryingIndex)
        searchableClassMappingConfigurator.configureAndInstallMappings()
    }

    /*
     * STRATEGY : none
     * case 1: Nothing exists
     * case 2: Conflict
     */

    void "An index is created when nothing exists"() {
        given: "That an index does not exist"
        es.deleteIndex catalogMapping.queryingIndex

        and: "Alias Configuration"
        grailsApplication.config.elasticSearch.migration = [strategy: "none"]

        expect:
        !es.indexExists(catalogMapping.queryingIndex)

        when: "Installing the mappings"
        searchableClassMappingConfigurator.installMappings([catalogMapping])

        then: "Indexing and Querying index are the same"
        catalogMapping.indexingIndex == catalogMapping.queryingIndex

        and: "A Simple configuration is created"
        !es.aliasExists(catalogMapping.queryingIndex)
        es.indexExists(catalogMapping.queryingIndex)
        es.mappingExists(catalogMapping.queryingIndex, catalogMapping.elasticTypeName)
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
        catalogMapping.indexingIndex == catalogMapping.queryingIndex
        es.indexExists(catalogMapping.queryingIndex)
        es.mappingExists catalogMapping.queryingIndex, catalogMapping.elasticTypeName

        and: "Documents are lost as maping was recreated"
        Catalog.search("ACME").total == 0

        and: "No alias was created"
        !es.aliasExists(catalogMapping.queryingIndex)

        cleanup:
        Catalog.findAll().each { it.delete() }
    }

    void "delete works on alias as well"() {

        given: "An alias pointing to a versioned index"
        es.deleteIndex catalogMapping.queryingIndex
        es.createIndex catalogMapping.queryingIndex, 0
        es.pointAliasTo catalogMapping.queryingIndex, catalogMapping.queryingIndex, 0
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
        catalogMapping.indexingIndex == catalogMapping.queryingIndex
        es.indexExists(catalogMapping.queryingIndex)
        es.aliasExists(catalogMapping.queryingIndex)
        es.indexExists(catalogMapping.queryingIndex, 0)
        es.indexPointedBy(catalogMapping.queryingIndex) == es.versionIndex(catalogMapping.queryingIndex, 0)
        Catalog.count() == 2
        Catalog.search("ACME").total == 2

        when: "Installing the conflicting mapping"
        searchableClassMappingConfigurator.installMappings([catalogMapping])

        then: "It succeeds"
        catalogMapping.indexingIndex == catalogMapping.queryingIndex
        es.indexExists(catalogMapping.queryingIndex)
        es.mappingExists catalogMapping.queryingIndex, catalogMapping.elasticTypeName

        and: "The alias was not modified"
        es.indexExists(catalogMapping.queryingIndex)
        es.aliasExists(catalogMapping.queryingIndex)
        es.indexExists(catalogMapping.queryingIndex, 0)
        es.indexPointedBy(catalogMapping.queryingIndex) == es.versionIndex(catalogMapping.queryingIndex, 0)

        and: "Documents are lost as mapping was recreated"
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
        es.deleteIndex catalogMapping.queryingIndex

        and: "Alias Configuration"
        grailsApplication.config.elasticSearch.migration = [strategy: "alias"]

        expect:
        !es.indexExists(catalogMapping.queryingIndex)

        when:
        searchableClassMappingConfigurator.installMappings([catalogMapping])

        then:
        catalogMapping.indexingIndex == catalogMapping.queryingIndex
        es.indexExists(catalogMapping.queryingIndex)
        es.aliasExists(catalogMapping.queryingIndex)
        es.indexExists(catalogMapping.queryingIndex, 0)
        es.indexPointedBy(catalogMapping.queryingIndex) == es.versionIndex(catalogMapping.queryingIndex, 0)
        es.mappingExists(catalogMapping.queryingIndex, catalogMapping.elasticTypeName)
    }

    void "With 'alias' strategy if alias exist, the next one is created"() {
        given: "A range of previously created versions"
        es.deleteIndex catalogMapping.queryingIndex
        (0..10).each {
            es.createIndex catalogMapping.queryingIndex, it
        }
        es.pointAliasTo catalogMapping.queryingIndex, catalogMapping.queryingIndex, 10
        searchableClassMappingConfigurator.configureAndInstallMappings()

        and: "Two different mapping conflicts on the same index"
        assert catalogMapping != itemMapping
        assert catalogMapping.queryingIndex == itemMapping.queryingIndex
        createConflictingCatalogMapping()
        createConflictingProductMapping()

        and: "Alias Configuration"
        grailsApplication.config.elasticSearch.migration = [strategy: "alias"]

        expect:
        es.indexExists catalogMapping.queryingIndex
        es.aliasExists catalogMapping.queryingIndex
        es.indexExists catalogMapping.queryingIndex, 10
        es.indexPointedBy(catalogMapping.queryingIndex) == es.versionIndex(catalogMapping.queryingIndex, 10)

        when:
        searchableClassMappingConfigurator.installMappings([catalogMapping, itemMapping])

        then: "A new version is created"
        catalogMapping.indexingIndex == catalogMapping.queryingIndex
        es.indexExists catalogMapping.queryingIndex
        es.aliasExists catalogMapping.queryingIndex
        es.indexExists catalogMapping.queryingIndex, 11
        es.mappingExists(catalogMapping.queryingIndex, catalogMapping.elasticTypeName)

        and: "Only one version is created and not a version per conflict"
        es.indexPointedBy(catalogMapping.queryingIndex) == es.versionIndex(catalogMapping.queryingIndex, 11)
        !es.indexExists(catalogMapping.queryingIndex, 12)

        and: "Others mappings are created as well"
        es.mappingExists(itemMapping.queryingIndex, itemMapping.elasticTypeName)

        cleanup:
        es.deleteIndex catalogMapping.queryingIndex
        (0..10).each {
            es.deleteIndex catalogMapping.queryingIndex, it
        }
    }

    void "With 'alias' strategy if index exists, decide whether to replace with alias based on config"() {
        given: "Two different mapping conflicts on the same index"
        assert catalogMapping != itemMapping
        assert catalogMapping.queryingIndex == itemMapping.queryingIndex
        createConflictingCatalogMapping()
        createConflictingProductMapping()

        and: "Existing content"
        new Catalog(company:"ACME", issue: 1).save(flush:true,failOnError: true)
        new Catalog(company:"ACME", issue: 2).save(flush:true,failOnError: true)
        new Item(name:"Road Runner Ultrafast Glue").save(flush:true, failOnError: true)
        elasticSearchService.index()
        elasticSearchAdminService.refresh()

        expect:
        es.indexExists catalogMapping.queryingIndex
        !es.aliasExists(catalogMapping.queryingIndex)
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
        catalogMapping.indexingIndex == catalogMapping.queryingIndex
        Catalog.count() == 2
        Catalog.search("ACME").total == 2
        Item.count() == 1
        Item.search("Glue").total == 1
        es.mappingExists(catalogMapping.queryingIndex, catalogMapping.elasticTypeName)
        es.mappingExists(itemMapping.queryingIndex, catalogMapping.elasticTypeName)

        when:
        grailsApplication.config.elasticSearch.migration = [strategy: "alias", "aliasReplacesIndex" : true]
        grailsApplication.config.elasticSearch.bulkIndexOnStartup = false //On the other cases content is recreated
        searchableClassMappingConfigurator.installMappings([catalogMapping])

        then: "Alias replaces the index"
        catalogMapping.indexingIndex == catalogMapping.queryingIndex
        es.indexExists(catalogMapping.queryingIndex)
        es.aliasExists(catalogMapping.queryingIndex)
        es.indexPointedBy(catalogMapping.queryingIndex) == es.versionIndex(catalogMapping.queryingIndex, 0)
        es.mappingExists(catalogMapping.queryingIndex, catalogMapping.elasticTypeName)

        and: "Content is lost, as the index is regenerated"
        Catalog.count() == 2
        Catalog.search("ACME").total == 0
        Item.count() == 1
        Item.search("Glue").total == 0

        and: "All mappings are recreated"
        es.mappingExists(catalogMapping.queryingIndex, catalogMapping.elasticTypeName)
        es.mappingExists(itemMapping.queryingIndex, catalogMapping.elasticTypeName)

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
        es.deleteIndex catalogMapping.queryingIndex
        es.createIndex catalogMapping.queryingIndex, 0
        es.pointAliasTo catalogMapping.queryingIndex, catalogMapping.queryingIndex, 0
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
        catalogMapping.queryingIndex == old(catalogMapping.queryingIndex)
        catalogMapping.indexingIndex == es.versionIndex(catalogMapping.queryingIndex, 1)
        catalogMapping.indexingIndex != catalogMapping.queryingIndex

        then: "All aliases, indexes and mappings exist"
        es.indexExists(catalogMapping.queryingIndex)
        es.aliasExists(catalogMapping.queryingIndex)
        es.indexExists(catalogMapping.indexingIndex)
        es.mappingExists(catalogMapping.queryingIndex, catalogMapping.elasticTypeName)
        es.mappingExists(itemMapping.queryingIndex, catalogMapping.elasticTypeName)
        es.mappingExists(catalogMapping.indexingIndex, catalogMapping.elasticTypeName)
        es.mappingExists(itemMapping.indexingIndex, catalogMapping.elasticTypeName)

        and: "the Alias is not updated until the new index is populated"
        es.indexPointedBy(catalogMapping.queryingIndex) == es.versionIndex(catalogMapping.queryingIndex, 0)

        and: "Content isn't lost as it keeps pointing to the old index"
        Catalog.search("ACME").total == 2
        Item.search("Glue").total == 1

        when: "Bootstrap runs"
        elasticSearchBooStrapHelper.bulkIndexOnStartup()
        and:
        elasticSearchAdminService.refresh()

        then: "The alias now points to the new index"
        es.indexPointedBy(catalogMapping.queryingIndex) == es.versionIndex(catalogMapping.queryingIndex, 1)

        and: "All indices point to the new location"
        catalogMapping.queryingIndex == old(catalogMapping.queryingIndex)
        catalogMapping.indexingIndex == catalogMapping.queryingIndex

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
        es.deleteMapping catalogMapping.queryingIndex, catalogMapping.elasticTypeName
        //Create conflicting Mapping
        catalogPagesMapping.addAttributes([component:true])
        searchableClassMappingConfigurator.installMappings([catalogMapping])
        //Restore initial state for next use
        catalogPagesMapping.addAttributes([component:'inner'])
    }

    private void createConflictingProductMapping() {
        //Delete existing Mapping
        es.deleteMapping itemMapping.queryingIndex, itemMapping.elasticTypeName
        //Create conflicting Mapping
        itemSupplierMapping.addAttributes([component:true])
        searchableClassMappingConfigurator.installMappings([itemMapping])
        //Restore initial state for next use
        itemSupplierMapping.addAttributes([component:'inner'])
    }
}

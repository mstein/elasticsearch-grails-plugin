package org.grails.plugins.elasticsearch.mapping

import grails.test.spock.IntegrationSpec
import org.elasticsearch.client.Client
import org.grails.plugins.elasticsearch.exception.MappingException
import test.mapping.migration.Catalog

/**
 * Created by @marcos-carceles on 07/01/15.
 */
class MappingMigrationSpec extends IntegrationSpec {

    def grailsApplication
    def searchableClassMappingConfigurator
    def elasticSearchService
    def elasticSearchAdminService
    def elasticSearchShortcuts

    def getEs() {
        elasticSearchShortcuts
    }

    def setup() {
        def dataFolder = new File('data')
        if (dataFolder.isDirectory()) {
            dataFolder.deleteDir()
        }
        searchableClassMappingConfigurator.configureAndInstallMappings()
    }

    /*
     * STRATEGY : none
     */

    def "when there's a conflict and no strategy is selected an exception is thrown"() {

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
     */

    def "when there's a conflict and strategy is 'delete' content is deleted"() {

        given: "A Conflicting Catalog mapping (with nested as opposed to inner pages)"
        createConflictingCatalogMapping()

        and: "Delete Configuration"
        grailsApplication.config.elasticSearch.migration = [strategy: "delete"]

        and: "Existing content"
        new Catalog(company:"ACME", issue: 1).save(flush:true,failOnError: true)
        new Catalog(company:"ACME", issue: 2).save(flush:true,failOnError: true)
        elasticSearchService.index()

        expect:
        Catalog.count() == 2
        Catalog.search("acme").total == 2

        when: "Installing the conflicting mapping"
        searchableClassMappingConfigurator.installMappings([catalogMapping])

        then: "It succeeds"
        es.mappingExists catalogMapping.indexName, catalogMapping.elasticTypeName

        and: "Documents are lost as maping was recreated"
        Catalog.search("ACME").total == 0

        cleanup:
        Catalog.list().each { it.delete() }

    }

    def _catalogMapping = null
    private SearchableClassMapping getCatalogMapping() {
        if (!_catalogMapping) {
            _catalogMapping = searchableClassMappingConfigurator.mappings().find { SearchableClassMapping it ->
                it.domainClass.clazz == Catalog
            }
        }
        _catalogMapping
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

}

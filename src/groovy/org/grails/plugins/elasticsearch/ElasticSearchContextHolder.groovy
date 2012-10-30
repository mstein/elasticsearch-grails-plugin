package org.grails.plugins.elasticsearch

import org.codehaus.groovy.grails.commons.GrailsDomainClass
import org.grails.plugins.elasticsearch.mapping.SearchableClassMapping

class ElasticSearchContextHolder {
    /**
     * The configuration of the ElasticSearch plugin
     */
    ConfigObject config

    /**
     * A map containing the mapping to ElasticSearch
     */
    Map<String, SearchableClassMapping> mapping = [:]
    
    /**
     * Map syntetic type (indexName.type) to full class name
     */
    Map<String, String> syntheticToFullTypeMapping = [:]

    /**
     * Adds a mapping context to the current mapping holder
     *
     * @param scm The SearchableClassMapping instance to add
     */
    public void addMappingContext(SearchableClassMapping scm) {
        mapping[scm.domainClass.fullName] = scm
        syntheticToFullTypeMapping[scm.getIndexName() + "." + scm.domainClass.shortName] = scm.domainClass.fullName
    }

    /**
     * Returns the mapping context for a peculiar type
     * @param type
     * @return
     */
    SearchableClassMapping getMappingContext(String type) {
        mapping[type]
    }
    
    /**
     * Returns the mapping context for a synthetic type
     * @param type
     * @return
     */
    SearchableClassMapping getMappingContextForSyntheticType(String type) {
        mapping[syntheticToFullTypeMapping[type]]
    }

    /**
     * Returns the mapping context for a peculiar GrailsDomainClass
     * @param domainClass
     * @return
     */
    SearchableClassMapping getMappingContext(GrailsDomainClass domainClass) {
        mapping[domainClass.fullName]
    }

    /**
     * Returns the mapping context for a peculiar Class
     *
     * @param clazz
     * @return
     */
    SearchableClassMapping getMappingContextByType(Class clazz) {
        mapping.values().find { scm -> scm.domainClass.clazz == clazz }
    }

    /**
     * Determines if a Class is root-mapped by the ElasticSearch plugin
     *
     * @param clazz
     * @return A boolean determining if the class is root-mapped or not
     */
    def isRootClass(Class clazz) {
        mapping.values().any { scm -> scm.domainClass.clazz == clazz && scm.root }
    }

    /**
     * Returns the Class that is associated to a specific elasticSearch type
     *
     * @param elasticTypeName
     * @return A Class instance or NULL if the class was not found
     */
    Class findMappedClassByElasticType(String elasticTypeName) {
        mapping.values().find { scm -> scm.elasticTypeName == elasticTypeName }?.domainClass?.clazz
    }
}

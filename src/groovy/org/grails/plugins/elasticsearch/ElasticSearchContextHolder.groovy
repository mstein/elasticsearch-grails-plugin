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
     * A Set containing all the mappings that were deleted and created again by the migration strategy
     */
    Set<Class> deletedOnMigration = [] as Set

    /**
     * Adds a mapping context to the current mapping holder
     *
     * @param scm The SearchableClassMapping instance to add
     */
    void addMappingContext(SearchableClassMapping scm) {
        mapping[scm.domainClass.fullName] = scm
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
    boolean isRootClass(Class clazz) {
        mapping.values().any { scm -> scm.domainClass.clazz == clazz && scm.root }
    }

    /**
     * Returns the Class that is associated to a specific elasticSearch type
     *
     * @param elasticTypeName
     * @return A Class instance or NULL if the class was not found
     */
    Class findMappedClassByElasticType(String elasticTypeName) {
        findMappingContextByElasticType(elasticTypeName)?.domainClass?.clazz
    }

    /**
     * Returns the SearchableClassMapping that is associated to a elasticSearch type
     * @param elasticTypeName
     * @return
     */
    SearchableClassMapping findMappingContextByElasticType(String elasticTypeName) {
        mapping.values().find { scm -> scm.elasticTypeName == elasticTypeName }
    }
}

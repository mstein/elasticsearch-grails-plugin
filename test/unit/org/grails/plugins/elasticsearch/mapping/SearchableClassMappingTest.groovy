package org.grails.plugins.elasticsearch.mapping

import grails.test.mixin.Mock

import org.codehaus.groovy.grails.commons.DefaultGrailsDomainClass
import org.codehaus.groovy.grails.commons.GrailsDomainClass
import org.junit.After
import org.junit.Test

import test.Photo
import test.upperCase.UpperCase

@Mock([Photo, UpperCase])
class SearchableClassMappingTest {

    @Test
    void testGetIndexName() {
        GrailsDomainClass dc = new DefaultGrailsDomainClass(Photo)
        dc.grailsApplication = grailsApplication
        SearchableClassMapping mapping = new SearchableClassMapping(dc,null)
        assert "test" == mapping.getIndexName()
    }

    @Test
    void testManuallyConfiguredIndexName() {
        GrailsDomainClass dc = new DefaultGrailsDomainClass(Photo)
        dc.grailsApplication = grailsApplication
        config.elasticSearch.index.name = "index-name"
        SearchableClassMapping mapping = new SearchableClassMapping(dc,null)
        assert "index-name" == mapping.getIndexName()
    }

    @Test
    void testIndexNameIsLowercaseWhenPackageNameIsLowercase() {
        GrailsDomainClass dc = new DefaultGrailsDomainClass(UpperCase)
        dc.grailsApplication = grailsApplication
        SearchableClassMapping mapping = new SearchableClassMapping(dc,null)
        String indexName = mapping.getIndexName()
        assert "test.uppercase" == indexName
    }

    @After
    void cleanup() {
        config.elasticSearch.index.name = null
    }
}

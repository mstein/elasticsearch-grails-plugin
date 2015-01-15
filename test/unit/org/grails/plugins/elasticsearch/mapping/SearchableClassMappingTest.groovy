package org.grails.plugins.elasticsearch.mapping

import grails.test.mixin.Mock
import org.codehaus.groovy.grails.commons.DefaultGrailsDomainClass
import org.codehaus.groovy.grails.commons.GrailsDomainClass
import org.junit.After
import org.junit.Test
import test.Photo
import test.upperCase.UpperCase

@Mock([Photo, UpperCase])
public class SearchableClassMappingTest {

    @Test
    public void testGetQueryingIndexName() throws Exception {
        GrailsDomainClass dc = new DefaultGrailsDomainClass(Photo.class)
        dc.grailsApplication = grailsApplication
        SearchableClassMapping mapping = new SearchableClassMapping(dc, null)
        assert 'test' == mapping.getQueryingIndex()
    }

    @Test
    public void testGetIndexingIndexName() throws Exception {
        GrailsDomainClass dc = new DefaultGrailsDomainClass(Photo.class)
        dc.grailsApplication = grailsApplication
        SearchableClassMapping mapping = new SearchableClassMapping(dc, null)
        assert 'test' == mapping.getIndexingIndex()
    }

    @Test
    public void testManuallyConfiguredIndexName() throws Exception {
        GrailsDomainClass dc = new DefaultGrailsDomainClass(Photo.class)
        dc.grailsApplication = grailsApplication
        config.elasticSearch.index.name = 'index-name'
        SearchableClassMapping mapping = new SearchableClassMapping(dc, null)
        assert 'index-name' == mapping.getQueryingIndex()
        assert 'index-name' == mapping.getIndexingIndex()
    }

    @Test
    public void testIndexNameIsLowercaseWhenPackageNameIsLowercase() throws Exception {
        GrailsDomainClass dc = new DefaultGrailsDomainClass(UpperCase.class)
        dc.grailsApplication = grailsApplication
        SearchableClassMapping mapping = new SearchableClassMapping(dc, null)
        assert 'test.uppercase' == mapping.getQueryingIndex()
        assert 'test.uppercase' == mapping.getIndexingIndex()
    }

    @Test
    public void testIndexingAndQueryingIndicesCanBeSetIndependently() throws Exception {
        GrailsDomainClass dc = new DefaultGrailsDomainClass(UpperCase.class)
        dc.grailsApplication = grailsApplication
        SearchableClassMapping mapping = new SearchableClassMapping(dc, null)
        mapping.setIndexingIndex("test.index_v1")
        mapping.setQueryingIndex("test.index")
        assert 'test.index_v1' == mapping.getIndexingIndex()
        assert 'test.index' == mapping.getQueryingIndex()
    }

    @After
    void cleanup() {
        config.elasticSearch.index.name = null
    }
}


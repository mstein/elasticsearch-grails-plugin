package org.grails.plugins.elasticsearch.mapping;


import org.junit.Test
import org.codehaus.groovy.grails.commons.GrailsDomainClass
import org.codehaus.groovy.grails.commons.DefaultGrailsDomainClass
import test.Photo
import test.upperCase.UpperCase

public class SearchableClassMappingTest {
    @Test
    public void testGetIndexName() throws Exception {
        GrailsDomainClass dc = new DefaultGrailsDomainClass(Photo.class);
        SearchableClassMapping mapping = new SearchableClassMapping(dc,null);
        assert "test" == mapping.getIndexName();
    }

    @Test
    public void testIndexNameIsLowercaseWhenPackageNameIsLowercase() throws Exception {
        GrailsDomainClass dc = new DefaultGrailsDomainClass(UpperCase.class);
        SearchableClassMapping mapping = new SearchableClassMapping(dc,null);
        String indexName = mapping.getIndexName();
        assert "test.uppercase" == indexName;
    }
}

package org.grails.plugins.elasticsearch.mapping

import org.codehaus.groovy.grails.commons.GrailsDomainClass
import spock.lang.Specification

import static org.grails.plugins.elasticsearch.mapping.SearchableClassMapping.READ_SUFFIX
import static org.grails.plugins.elasticsearch.mapping.SearchableClassMapping.WRITE_SUFFIX

class SearchableClassMappingSpec extends Specification {

    def "indexing and querying index are calculated based on the index name"() {
        given:
        def domainClass = Mock(GrailsDomainClass)
        domainClass.getPackageName() >> packageName

        when:
        SearchableClassMapping scm = new SearchableClassMapping(domainClass, [])

        then:
        scm.indexName == packageName
        scm.queryingIndex == packageName + READ_SUFFIX
        scm.indexingIndex == packageName + WRITE_SUFFIX
        scm.queryingIndex != scm.indexingIndex
        scm.indexName != scm.queryingIndex
        scm.indexName != scm.indexingIndex

        where:
        packageName << ["test.scm", "com.mapping"]
    }
}

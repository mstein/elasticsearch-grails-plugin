package org.grails.plugins.elasticsearch.conversion.unmarshall

import grails.test.spock.IntegrationSpec
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.elasticsearch.common.bytes.BytesArray
import org.elasticsearch.common.text.StringAndBytesText
import org.elasticsearch.search.internal.InternalSearchHit
import org.elasticsearch.search.internal.InternalSearchHits
import org.grails.plugins.elasticsearch.ElasticSearchContextHolder
import org.grails.plugins.elasticsearch.exception.MappingException
import org.slf4j.Logger
import test.GeoPoint

import java.lang.reflect.Field
import java.lang.reflect.Modifier

class DomainClassUnmarshallerIntegrationSpec extends IntegrationSpec {

    ElasticSearchContextHolder elasticSearchContextHolder
    GrailsApplication grailsApplication

    def cleanupSpec() {
        def dataFolder = new File('data')
        if (dataFolder.isDirectory()) {
            dataFolder.deleteDir()
        }
    }

    void 'An unmarshalled geo_point is marshalled into a GeoPoint domain object'() {
        def unmarshaller = new DomainClassUnmarshaller(elasticSearchContextHolder: elasticSearchContextHolder, grailsApplication: grailsApplication)

        given: 'a search hit with a geo_point'
        InternalSearchHit hit = new InternalSearchHit(1, '1', new StringAndBytesText('building'), [:])
                .sourceRef(new BytesArray('{"location":{"class":"test.GeoPoint","id":"2", "lat":53.0,"lon":10.0},"name":"WatchTower"}'))
        InternalSearchHit[] hits = [hit]
        def maxScore = 0.1534264087677002f
        def totalHits = 1
        def searchHits = new InternalSearchHits(hits, totalHits, maxScore)

        when: 'an geo_point is unmarshalled'
        def results = unmarshaller.buildResults(searchHits)
        results.size() == 1

        then: 'this results in a GeoPoint domain object'
        results[0].name == 'WatchTower'
        def location = results[0].location
        location.class == GeoPoint
        location.lat == 53.0
        location.lon == 10.0
    }

    void 'An unhandled property in the indexed domain root is ignored'() {
        def unmarshaller = new DomainClassUnmarshaller(elasticSearchContextHolder: elasticSearchContextHolder, grailsApplication: grailsApplication)

        given: 'a search hit with a color with unhandled properties r-g-b'
        InternalSearchHit hit = new InternalSearchHit(1, '1', new StringAndBytesText('color'), [:])
                .sourceRef(new BytesArray('{"name":"Orange", "red":255, "green":153, "blue":0}'))
        InternalSearchHit[] hits = [hit]
        def maxScore = 0.1534264087677002f
        def totalHits = 1
        def searchHits = new InternalSearchHits(hits, totalHits, maxScore)
        GroovySpy(MappingException, global: true)

        when: 'the color is unmarshalled'
        def results
        withMockLogger {
            results = unmarshaller.buildResults(searchHits)
        }
        results.size() == 1

        then: 'this results in a color domain object'
        1 * new MappingException('Property Color.red found in index, but is not defined as searchable.')
        1 * new MappingException('Property Color.green found in index, but is not defined as searchable.')
        1 * new MappingException('Property Color.blue found in index, but is not defined as searchable.')
        0 * new MappingException(_)
        results[0].name == 'Orange'
        results[0].red == null
        results[0].green == null
        results[0].blue == null
    }

    void 'An unhandled property in an embedded indexed domain is ignored'() {
        def unmarshaller = new DomainClassUnmarshaller(elasticSearchContextHolder: elasticSearchContextHolder, grailsApplication: grailsApplication)

        given: 'a search hit with a circle, within it a color with an unhandled properties "red"'
        InternalSearchHit hit = new InternalSearchHit(1, '1', new StringAndBytesText('circle'), [:])
                .sourceRef(new BytesArray('{"radius":7, "color":{"class":"test.Color", "id":"2", "name":"Orange", "red":255}}'))
        InternalSearchHit[] hits = [hit]
        def maxScore = 0.1534264087677002f
        def totalHits = 1
        def searchHits = new InternalSearchHits(hits, totalHits, maxScore)
        GroovySpy(MappingException, global: true)

        when: 'the circle is unmarshalled'
        def results
        withMockLogger {
            results = unmarshaller.buildResults(searchHits)
        }
        results.size() == 1

        then: 'this results in a circle domain object with color'
        1 * new MappingException('Property Color.red found in index, but is not defined as searchable.')
        0 * new MappingException(_)
        results[0].radius == 7
        def color = results[0].color
        color.name == 'Orange'
        color.red == null
        color.green == null
        color.blue == null
    }

    private void withMockLogger(Closure closure) {
        def logField = DomainClassUnmarshaller.class.getDeclaredField('LOG')
        logField.setAccessible(true)
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(logField, logField.getModifiers() & ~Modifier.FINAL);

        Logger origLog = logField.get(null)
        Logger mockLog = Mock(Logger) {
            debug(_ as String) >> { String s -> if (origLog.debugEnabled) println("DEBUG: $s") }
            debug(_ as String, _ as Throwable) >> { String s, Throwable t -> if (origLog.debugEnabled) { println("DEBUG: $s"); t.printStackTrace(System.out) } }
            error(_ as String) >> { String s -> System.err.println("ERROR: $s") }
            error(_ as String, _ as Throwable) >> { String s, Throwable t -> System.err.println("ERROR: $s"); t.printStackTrace() }
        }
        try {
            logField.set(null, mockLog)
            closure.call()
        } finally {
            logField.set(null, origLog)
        }
    }
}

package org.grails.plugins.elasticsearch

import grails.test.mixin.TestFor
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Created by @marcos-carceles on 15/01/15.
 */
@TestFor(ElasticSearchAdminService)
class ElasticSearchAdminServiceSpec extends Specification {

    @Unroll
    void "identifies the next index version correctly"() {
        given:
        indices = indices as Set
        service.metaClass.getIndices = {-> indices}
        expect:
        service.getNextVersion("my.app") == expectedNext

        where:
        indices                                          | expectedNext
        []                                               | 0
        ['another.app']                                  | 0
        ['my.app']                                       | 0
        ['my.app_v0']                                    | 1
        ['my.app', 'another.app']                        | 0
        ['my.app', 'my.app_v0']                          | 1
        ['my.app', 'my.app_v0', 'another.app']           | 1
        ['my.app', 'my.app_v0', 'my.app_v1']             | 2
        ['my.app', 'my.app_v2', 'my.app_v1']             | 3
        ['my.app'] + (0..10).collect { 'my.app_v' + it } | 11
    }
}

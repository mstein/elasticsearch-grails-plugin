package org.grails.plugins.elasticsearch.exception

class IndexException extends RuntimeException {

    IndexException(String s) {
        super(s)
    }

    IndexException(String s, Throwable throwable) {
        super(s, throwable)
    }
}

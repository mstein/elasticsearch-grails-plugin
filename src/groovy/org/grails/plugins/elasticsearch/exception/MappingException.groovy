package org.grails.plugins.elasticsearch.exception

/**
 * Created by @marcos-carceles on 07/01/15.
 */
class MappingException extends Exception {

    MappingException() {
        super()
    }

    public MappingException(String message) {
        super(message)
    }

    public MappingException(String message, Throwable cause) {
        super(message, cause)
    }
}

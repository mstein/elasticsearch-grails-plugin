package org.grails.plugins.elasticsearch.conversion.unmarshall

class CycleReferenceSource {
    /** The path to the cycle reference */
    String cyclePath
    /** The path to the source to use to populate the reference */
    String sourcePath
}

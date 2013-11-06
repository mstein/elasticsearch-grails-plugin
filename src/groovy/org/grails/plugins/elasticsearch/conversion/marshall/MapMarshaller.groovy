package org.grails.plugins.elasticsearch.conversion.marshall

class MapMarshaller extends DefaultMarshaller {

    protected doMarshall(map) {
        def marshallResult = [:]
        map.each { key, value ->
            if (value instanceof Map) {
                marshallResult."${key}" = marshall(value)
            } else {
                marshallResult."${key}" = marshallingContext.delegateMarshalling(value)
            }
        }
        return marshallResult
    }

    protected nullValue(){
        return [:]
    }
}

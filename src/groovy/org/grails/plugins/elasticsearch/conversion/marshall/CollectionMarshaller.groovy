package org.grails.plugins.elasticsearch.conversion.marshall

class CollectionMarshaller extends DefaultMarshaller {
    protected doMarshall(collection) {
        def marshallResult = collection.asList().collect {
            marshallingContext.delegateMarshalling(it)
        }
        return marshallResult
    }

    protected nullValue() {
        return []
    }
}

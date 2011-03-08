package org.grails.plugins.elasticsearch.conversion.marshall

import org.hibernate.Hibernate

class CollectionMarshaller extends DefaultMarshaller {
    protected doMarshall(collection) {
        Hibernate.initialize(collection)
        def marshallResult = collection.asList().collect {
            marshallingContext.delegateMarshalling(it)
        }
        return marshallResult
    }

    protected nullValue() {
        return []
    }
}

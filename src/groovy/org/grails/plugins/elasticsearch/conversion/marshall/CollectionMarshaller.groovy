package org.grails.plugins.elasticsearch.conversion.marshall

class CollectionMarshaller extends DefaultMarshaller {
  protected doMarshall(collection) {
    def marshallResult = collection.collect {
      if (it instanceof Collection) {
        marshall(it)
      } else {
        marshallingContext.delegateMarshalling(it)
      }
    }
    return marshallResult
  }

  protected nullValue(){
    return []
  }
}

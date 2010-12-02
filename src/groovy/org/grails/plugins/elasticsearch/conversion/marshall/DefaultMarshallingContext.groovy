package org.grails.plugins.elasticsearch.conversion.marshall

class DefaultMarshallingContext {
  def parentFactory
  def maxDepth = 5
  Stack marshallStack = new Stack()
  def marshalled = [:]
  def unmarshalled = [:]
  def currentDepth = 0

  public Boolean isMarshalled(type, id) {

  }

  public delegateMarshalling(object){
    parentFactory.delegateMarshalling(object, this)
  }
}

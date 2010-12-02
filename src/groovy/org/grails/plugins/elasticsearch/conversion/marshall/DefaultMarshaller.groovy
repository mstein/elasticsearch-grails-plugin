package org.grails.plugins.elasticsearch.conversion.marshall

class DefaultMarshaller implements Marshaller {
  DefaultMarshallingContext marshallingContext
  def elasticSearchContextHolder

  /**
   * Marshall the object considering the marshallingContext maxDepth.
   * If the maxDepth is reached, return the nullValue content.
   * @param object
   * @return
   */
  public marshall(object){
    def marshallResult = nullValue()
    if(object == null) {
      return nullValue()
    }
    // Cycle detection
    def cycleIndex = marshallingContext.marshallStack.indexOf(object)
    if(cycleIndex != -1) {
      def ref = []
      def refPos = marshallingContext.marshallStack.size() - cycleIndex
      refPos.times {
        ref << '..'
      }
      return ['class':object.class?.name, ref:ref.join('/')]
    }
    // Only marshall if the maxDepth has not been reached
    if(marshallingContext.currentDepth <= marshallingContext.maxDepth) {
      marshallingContext.currentDepth++
      marshallingContext.marshallStack.push(object)
      marshallResult = this.doMarshall(object)
      marshallingContext.marshallStack.pop()
      marshallingContext.currentDepth--
    }

    return marshallResult
  }

  @Override
  protected doMarshall(object){
    return object
  }

  protected nullValue(){
    return ''
  }
}

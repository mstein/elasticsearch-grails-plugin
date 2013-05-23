package org.grails.plugins.elasticsearch.conversion.marshall

class DefaultMarshaller implements Marshaller {
    DefaultMarshallingContext marshallingContext
    def elasticSearchContextHolder
    def maxDepth
    def grailsApplication

    /**
     * Marshall the object considering the marshallingContext maxDepth.
     * If the maxDepth is reached, return the nullValue content.
     * @param object
     * @param maxDepth object browsing max depth
     * @return marshall result (Map/String/etc.)
     */
    public marshall(object) {
        def marshallResult = nullValue()
        if (object == null) {
            return nullValue()
        }
        // Cycle detection
        def cycleIndex = marshallingContext.marshallStack.findIndexOf { it.instance.is(object) }
        if (cycleIndex != -1) {
            def ref = []
            def refPos = marshallingContext.marshallStack.size() - cycleIndex
            refPos.times {
                ref << '..'
            }
            return ['class': object.class?.name, ref: ref.join('/')]
        }
        // Only marshall if the maxDepth has not been reached
        if (marshallingContext.push(object, this.maxDepth)) {
//        if (marshallingContext.currentDepth <= marshallingContext.maxDepth) {
//            marshallingContext.currentDepth++
            marshallResult = this.doMarshall(object)
            marshallingContext.pop()
//            marshallingContext.currentDepth--
        } // otherwise returns nullValue

        return marshallResult
    }

    protected doMarshall(object) {
        return object
    }

    protected nullValue() {
        return ''
    }
}

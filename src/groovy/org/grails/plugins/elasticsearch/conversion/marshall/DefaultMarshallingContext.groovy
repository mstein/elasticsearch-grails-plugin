/*
 * Copyright 2002-2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.plugins.elasticsearch.conversion.marshall

import org.grails.plugins.elasticsearch.conversion.JSONDomainFactory

class DefaultMarshallingContext {
    JSONDomainFactory parentFactory
    def maxDepth = 5
    Stack<MarshalledObject> marshallStack = new Stack<MarshalledObject>()
    def marshalled = [:]
    def unmarshalled = [:]
    def currentDepth = 0
    def lastParentPropertyName = ''

    /**
     * Push object instance on top of the stack
     * and calculate maxDepth based on current stack state.
     * @param instance instance to push
     */
    boolean push(instance) {
        def depth = 5 //marshallStack.empty ? maxDepth : marshallStack.peek().maxDepth - 1
        if (depth <= 0) {
            return false
        }
        push(instance, depth)
        return true
    }

    /**
     * Push object instance on top of the stack
     * with assigned maxDepth property.
     * @param instance instance to push
     * @param maxDepth remained maxDepth
     */
    def push(instance, maxDepth) {
        marshallStack.push(new MarshalledObject(instance:instance,maxDepth:maxDepth ?: this.maxDepth))
    }

    def pop() {
        marshallStack.pop()?.instance
    }

    def peekDomainObject() {
        if (marshallStack.empty) {
            return null
        }
        def o = marshallStack.peek().instance
        // Parent could be a persistent collection. In this case,
        // try grandparent as a workaround.
        if (o instanceof Collection || o instanceof Map) {
            def top = marshallStack.pop()
            o = marshallStack.peek().instance
            marshallStack.push(top)
        }
        o
    }

    def delegateMarshalling(object, maxDepth = 0) {
        parentFactory.delegateMarshalling(object, this, maxDepth)
    }

    static class MarshalledObject {
        def instance
        def maxDepth
    }
}

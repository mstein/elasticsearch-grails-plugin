/*
 * Copyright 2002-2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.grails.plugins.elasticsearch.conversion

import org.codehaus.groovy.grails.commons.GrailsDomainClass
import org.codehaus.groovy.grails.web.metaclass.BindDynamicMethod
import org.springframework.beans.SimpleTypeConverter
import org.elasticsearch.search.SearchHit
import org.grails.plugins.elasticsearch.conversion.unmarshall.DefaultUnmarshallingContext
import org.grails.plugins.elasticsearch.conversion.unmarshall.CycleReferenceSource
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.grails.plugins.elasticsearch.ElasticSearchContextHolder

class DomainInstancesRebuilder {

    ElasticSearchContextHolder elasticSearchContextHolder
    GrailsApplication grailsApplication

    public Collection buildResults(hits) {
        def typeConverter = new SimpleTypeConverter()
        BindDynamicMethod bind = new BindDynamicMethod()
        def unmarshallingContext = new DefaultUnmarshallingContext()
        def results = []
        for (SearchHit hit in hits) {
            def packageName = hit.index()
            def domainClassName = packageName == hit.type() ? packageName : packageName + '.' + hit.type().substring(0, 1).toUpperCase() + hit.type().substring(1)
            def domainClass = grailsApplication.getDomainClass(domainClassName)
            if (!domainClass) {
                continue
            }
            /*def mapContext = elasticSearchContextHolder.getMappingContext(domainClass.propertyName)?.propertiesMapping*/
            def identifier = domainClass.getIdentifier()
            def id = typeConverter.convertIfNecessary(hit.id(), identifier.getType())
            def instance = domainClass.newInstance()
            instance."${identifier.name}" = id
            def rebuiltProperties = [:]
            hit.source.each { name, value ->
                unmarshallingContext.unmarshallingStack.push(name)
                rebuiltProperties += [(name): buildProperty(domainClass, value, unmarshallingContext)]
                populateCyclicReference(instance, rebuiltProperties, unmarshallingContext)
                unmarshallingContext.resetContext()
            }
            def args = [instance, rebuiltProperties] as Object[]
            bind.invoke(instance, 'bind', args)

            results << instance
        }
        return results
    }

    private populateCyclicReference(instance, rebuiltProperties, unmarshallingContext) {
        unmarshallingContext.cycleRefStack.each { CycleReferenceSource cr ->
            populateProperty(cr.cyclePath, rebuiltProperties, resolvePath(cr.sourcePath, instance, rebuiltProperties))
        }
    }

    private resolvePath(path, instance, rebuiltProperties) {
        if (!path) {
            return instance
        } else {
            def splitted = path.split('/').toList()
            def currentProperty = rebuiltProperties
            splitted.each {
                if (it.isNumber())
                    currentProperty = currentProperty.asList()[it.toInteger()]
                else
                    currentProperty = currentProperty[it]
            }
            return currentProperty
        }
    }

    private populateProperty(path, rebuiltProperties, value) {
        def splitted = path.split('/').toList()
        def last = splitted.last()
        def currentProperty = rebuiltProperties
        def size = splitted.size()
        splitted.eachWithIndex { it, index ->
            if (index < size - 1) {
                try {
                    if (currentProperty instanceof Collection) {
                        currentProperty = currentProperty.asList()[it.toInteger()]
                    } else {
                        currentProperty = currentProperty.getAt(it)
                    }
                } catch (Exception e) {
                    LOG.warn("/!\\ Error when trying to populate ${path}")
                    LOG.warn("Cannot get ${it} on ${currentProperty} from ${rebuiltProperties}")
                    e.printStackTrace()
                }
            }
        }
        if (last.isNumber()) {
            currentProperty << value
        } else {
            currentProperty."${last}" = value
        }

    }

    private Boolean isDomain(Map data) {
        if (data instanceof Map && data.'class') {
            return grailsApplication.domainClasses.any { it.clazz.name == data.'class' }
        }
        return false
    }

    private Object buildProperty(GrailsDomainClass domainClass, propertyValue, unmarshallingContext) {
        // TODO : adapt behavior if the mapping option "component" or "reference" are set
        // below is considering the "component" behavior
        def parseResult = propertyValue
        if (propertyValue instanceof Map) {
            // Handle cycle reference
            if (propertyValue.ref) {
                unmarshallingContext.addCycleRef(propertyValue)
                return null
            }
            if (isDomain(propertyValue)) {
                parseResult = buildDomain(propertyValue, unmarshallingContext)
            } else {
                parseResult = propertyValue
            }
        } else if (propertyValue instanceof Collection) {
            parseResult = []
            propertyValue.eachWithIndex { value, index ->
                unmarshallingContext.unmarshallingStack.push(index)
                def built = buildProperty(domainClass, value, unmarshallingContext)
                if (built)
                    parseResult << built
                unmarshallingContext.unmarshallingStack.pop()
            }
        } else {
            // consider any custom property editors here.
            def propertyMapping = elasticSearchContextHolder.getMappingContext(domainClass)?.getPropertyMapping(unmarshallingContext.unmarshallingStack.peek())
            if (propertyMapping?.converter) {
                if (propertyMapping.converter instanceof Class) {
                    def propertyEditor = propertyMapping.converter.newInstance()
                    propertyEditor.setAsText(propertyValue)
                    parseResult = propertyEditor.value
                }
            }
        }
        return parseResult
    }

    private Object buildDomain(data, unmarshallingContext) {
        def domainClass = grailsApplication.domainClasses.find { it.clazz.name == data.'class' }
        def typeConverter = new SimpleTypeConverter()

        if (domainClass) {
            def identifier = domainClass.getIdentifier()
            def id = typeConverter.convertIfNecessary(data.id, identifier.getType())
            def instance = domainClass.newInstance()
            instance."${identifier.name}" = id
            BindDynamicMethod bind = new BindDynamicMethod()

            data.each { key, value ->
                if (key != 'class' && key != 'id') {
                    unmarshallingContext.unmarshallingStack.push(key)
                    def args = [instance, [(key): buildProperty(domainClass, value, unmarshallingContext)]] as Object[]
                    bind.invoke(instance, 'bind', args)
                    unmarshallingContext.unmarshallingStack.pop()
                }
            }
            return instance
        }
        return null
    }
}

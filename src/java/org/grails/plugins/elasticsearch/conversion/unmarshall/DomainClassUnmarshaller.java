/*
 * Copyright 2002-2011 the original author or authors.
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

package org.grails.plugins.elasticsearch.conversion.unmarshall;

import groovy.lang.GroovyObject;
import org.apache.log4j.Logger;
import org.codehaus.groovy.grails.commons.*;
import org.codehaus.groovy.grails.web.metaclass.BindDynamicMethod;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.grails.plugins.elasticsearch.ElasticSearchContextHolder;
import org.grails.plugins.elasticsearch.mapping.SearchableClassMapping;
import org.grails.plugins.elasticsearch.mapping.SearchableClassPropertyMapping;
import org.springframework.beans.SimpleTypeConverter;
import org.springframework.beans.TypeConverter;

import java.beans.PropertyEditor;
import java.util.*;

/**
 * Domain class unmarshaller.
 */
public class DomainClassUnmarshaller {

    private static final Logger LOG = Logger.getLogger(DomainClassUnmarshaller.class);

    private TypeConverter typeConverter = new SimpleTypeConverter();
    private ElasticSearchContextHolder elasticSearchContextHolder;
    private BindDynamicMethod bind = new BindDynamicMethod();
    private GrailsApplication grailsApplication;
    private Client elasticSearchClient;


    public Collection buildResults(SearchHits hits) {
        DefaultUnmarshallingContext unmarshallingContext = new DefaultUnmarshallingContext();
        List results = new ArrayList();
        for(SearchHit hit : hits) {
            String domainClassName = hit.index().equals(hit.type()) ? DefaultGroovyMethods.capitalize(hit.index()) : (hit.index() + '.' + DefaultGroovyMethods.capitalize(hit.type()));
            SearchableClassMapping scm = elasticSearchContextHolder.getMappingContextForSyntheticType(domainClassName);
            if (scm == null) {
                LOG.warn("Unknown SearchHit: " + hit.id() + "#" + hit.type() + ", domain class name: " + domainClassName);
                continue;
            }

            GrailsDomainClassProperty identifier = scm.getDomainClass().getIdentifier();
            Object id = typeConverter.convertIfNecessary(hit.id(), identifier.getType());
            GroovyObject instance = (GroovyObject) scm.getDomainClass().newInstance();
            instance.setProperty(identifier.getName(), id);

            /*def mapContext = elasticSearchContextHolder.getMappingContext(domainClass.propertyName)?.propertiesMapping*/
            Map rebuiltProperties = new HashMap();
            for(Map.Entry<String, Object> entry : hit.getSource().entrySet()) {
                unmarshallingContext.getUnmarshallingStack().push(entry.getKey());
                rebuiltProperties.put(entry.getKey(),
                        unmarshallProperty(scm.getDomainClass(), entry.getKey(), entry.getValue(), unmarshallingContext));
                populateCyclicReference(instance, rebuiltProperties, unmarshallingContext);
                unmarshallingContext.resetContext();
            }
            // todo manage read-only transient properties...
            bind.invoke(instance, "bind", new Object[] { instance, rebuiltProperties });

            results.add(instance);
        }
        return results;
    }

    private void populateCyclicReference(Object instance, Map<String, Object> rebuiltProperties, DefaultUnmarshallingContext unmarshallingContext) {
        for(CycleReferenceSource cr : unmarshallingContext.getCycleRefStack()) {
            populateProperty(cr.getCyclePath(), rebuiltProperties, resolvePath(cr.getSourcePath(), instance, rebuiltProperties));
        }
    }

    private Object resolvePath(String path, Object instance, Map<String, Object> rebuiltProperties) {
        if (path == null || path.equals("")) {
            return instance;
        } else {
            StringTokenizer st = new StringTokenizer(path, "/");
            Object currentProperty = rebuiltProperties;
            while (st.hasMoreTokens()) {
                String part = st.nextToken();
                try {
                    int index = Integer.parseInt(part);
                    currentProperty = DefaultGroovyMethods.getAt(DefaultGroovyMethods.asList((Collection) currentProperty), index);
                } catch (NumberFormatException e) {
                    currentProperty = DefaultGroovyMethods.getAt(currentProperty, part);
                }
            }
            return currentProperty;
        }
    }

    private void populateProperty(String path, Map<String, Object> rebuiltProperties, Object value) {
        String last = null;
        Object currentProperty = rebuiltProperties;
        StringTokenizer st = new StringTokenizer(path, "/");
        int size = st.countTokens();
        int index = 0;
        while (st.hasMoreTokens()) {
            String part = st.nextToken();
            if (index < size - 1) {
                try {
                    if (currentProperty instanceof Collection) {
                        //noinspection unchecked
                        currentProperty = DefaultGroovyMethods.getAt(((Collection<Object>) currentProperty).iterator(), DefaultGroovyMethods.toInteger(part));
                    } else {
                        currentProperty = DefaultGroovyMethods.getAt(currentProperty, part);
                    }
                } catch (Exception e) {
                    LOG.warn("/!\\ Error when trying to populate " + path);
                    LOG.warn("Cannot get " + part + " on " + currentProperty + " from " + rebuiltProperties);
                    e.printStackTrace();
                }
            }
            if (!st.hasMoreTokens()) {
                last = part;
            }
            index++;
        }
        try {
            Integer.parseInt(last);
            ((Collection) currentProperty).add(value);
        } catch (NumberFormatException e) {
            DefaultGroovyMethods.putAt(currentProperty, last, value);
        }
    }

    private Object unmarshallProperty(GrailsDomainClass domainClass, String propertyName, Object propertyValue, DefaultUnmarshallingContext unmarshallingContext) {
        // TODO : adapt behavior if the mapping option "component" or "reference" are set
        // below is considering the "component" behavior
        SearchableClassPropertyMapping scpm = elasticSearchContextHolder.getMappingContext(domainClass).getPropertyMapping(propertyName);
        Object parseResult = null;
        if (null == scpm) {
            // TODO: unhandled property exists in index
        }
        if (null != scpm && propertyValue instanceof Map) {

            @SuppressWarnings({"unchecked"})
            Map<String, Object> data = (Map<String, Object>) propertyValue;

            // Handle cycle reference
            if (data.containsKey("ref")) {
                unmarshallingContext.addCycleRef(propertyValue);
                return null;
            }

            // Searchable reference.
            if (scpm.getReference() != null) {
                Class<?> refClass = scpm.getBestGuessReferenceType();
                GrailsDomainClass refDomainClass = null;
                for(GrailsClass dClazz : grailsApplication.getArtefacts(DomainClassArtefactHandler.TYPE)) {
                    if (dClazz.getClazz().equals(refClass)) {
                        refDomainClass = (GrailsDomainClass) dClazz;
                        break;
                    }
                }
                if (refDomainClass == null) {
                    throw new IllegalStateException("Found reference to non-domain class: " + refClass);
                }
                return unmarshallReference(refDomainClass, data, unmarshallingContext);
            }

            if (data.containsKey("class")) {
                // Embedded instance.
                if (!scpm.isComponent()) {
                    // maybe ignore?
                    throw new IllegalStateException("Property " + domainClass.getName() + "." + propertyName +
                                " is not mapped as [component], but broken search hit found.");
                }
                GrailsDomainClass nestedDomainClass = (GrailsDomainClass)
                        grailsApplication.getArtefact(DomainClassArtefactHandler.TYPE, (String) data.get("class"));
                if (domainClass != null) {
                    // Unmarshall 'component' instance.
                    if (!scpm.isComponent()) {
                        throw new IllegalStateException("Object " + data.get("class") +
                                " found in index, but [" + propertyName + "] is not mapped as component.");
                    }
                    parseResult = unmarshallDomain(nestedDomainClass, data.get("id"), data, unmarshallingContext);
                }
            }
        } else if (propertyValue instanceof Collection) {
            List<Object> results = new ArrayList<Object>();
            int index = 0;
            for(Object innerValue : (Collection) propertyValue) {
                unmarshallingContext.getUnmarshallingStack().push(String.valueOf(index));
                Object parseItem = unmarshallProperty(domainClass, propertyName, innerValue, unmarshallingContext);
                if (parseItem != null) {
                    results.add(parseItem);
                }
                index++;
                unmarshallingContext.getUnmarshallingStack().pop();
            }
            parseResult = results;
        } else {
            // consider any custom property editors here.
            if (scpm.getConverter() != null) {
                if (scpm.getConverter() instanceof Class) {
                    try {
                        PropertyEditor propertyEditor = (PropertyEditor) ((Class) scpm.getConverter()).newInstance();
                        propertyEditor.setAsText((String) propertyValue);
                        parseResult = propertyEditor.getValue();
                    } catch (Exception e) {
                        throw new IllegalArgumentException("Unable to unmarshall " + propertyName +
                                " using " + scpm.getConverter(), e);
                    }
                }
            } else if (scpm.getReference() != null) {

                // This is a reference and it MUST be null because it's not a Map.
                if (propertyValue != null) {
                    throw new IllegalStateException("Found searchable reference which is not a Map: " + domainClass + "." + propertyName +
                            " = " + propertyValue);
                }

                parseResult = null;
            }
        }
        if (parseResult != null) {
            return parseResult;
        } else {
            return propertyValue;
        }
    }


    private Object unmarshallReference(GrailsDomainClass domainClass, Map<String, Object> data, DefaultUnmarshallingContext unmarshallingContext) {
        // As a simplest scenario recover object directly from ElasticSearch.
        // todo add first-level caching and cycle ref checking
        String indexName = elasticSearchContextHolder.getMappingContext(domainClass).getIndexName();
        String name = elasticSearchContextHolder.getMappingContext(domainClass).getElasticTypeName();
        // A property value is expected to be a map in the form [id:ident]
        Object id = data.get("id");
        GetResponse response = elasticSearchClient.get(new GetRequest(indexName)
                .operationThreaded(false)
                .type(name)
                .id(typeConverter.convertIfNecessary(id, String.class)))
                .actionGet();
        return unmarshallDomain(domainClass, response.id(), response.sourceAsMap(), unmarshallingContext);
    }


    private Object unmarshallDomain(GrailsDomainClass domainClass, Object providedId, Map<String, Object> data, DefaultUnmarshallingContext unmarshallingContext) {
        GrailsDomainClassProperty identifier = domainClass.getIdentifier();
        Object id = typeConverter.convertIfNecessary(providedId, identifier.getType());
        GroovyObject instance = (GroovyObject) domainClass.newInstance();
        instance.setProperty(identifier.getName(), id);
        for(Map.Entry<String, Object> entry : data.entrySet()) {
            if (!entry.getKey().equals("class") && !entry.getKey().equals("id")) {
                unmarshallingContext.getUnmarshallingStack().push(entry.getKey());
                Object propertyValue = unmarshallProperty(domainClass, entry.getKey(), entry.getValue(), unmarshallingContext);
                bind.invoke(instance, "bind", new Object[] { instance, Collections.singletonMap(entry.getKey(), propertyValue) });
                unmarshallingContext.getUnmarshallingStack().pop();
            }
        }
        return instance;
    }

    public void setElasticSearchContextHolder(ElasticSearchContextHolder elasticSearchContextHolder) {
        this.elasticSearchContextHolder = elasticSearchContextHolder;
    }

    public void setGrailsApplication(GrailsApplication grailsApplication) {
        this.grailsApplication = grailsApplication;
    }

    public void setElasticSearchClient(Client elasticSearchClient) {
        this.elasticSearchClient = elasticSearchClient;
    }
}

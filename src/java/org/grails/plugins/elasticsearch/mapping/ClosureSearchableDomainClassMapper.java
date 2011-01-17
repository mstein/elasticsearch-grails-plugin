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

package org.grails.plugins.elasticsearch.mapping;

import groovy.lang.Closure;
import groovy.lang.GroovyObjectSupport;
import groovy.util.ConfigObject;
import org.codehaus.groovy.grails.commons.GrailsDomainClass;
import org.codehaus.groovy.grails.commons.GrailsDomainClassProperty;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.*;

class ClosureSearchableDomainClassMapper extends GroovyObjectSupport {
    /** Options applied to searchable class itself */
    public static final Set<String> CLASS_MAPPING_OPTIONS = new HashSet<String>(Arrays.asList("all","root"));

    /** Class mapping properties */
    private Boolean all;
    private Boolean root;

    private Set<String> mappableProperties = new HashSet<String>();
    private Map<String, SearchableClassPropertyMapping> customMappedProperties = new HashMap<String, SearchableClassPropertyMapping>();
    private GrailsDomainClass grailsDomainClass;
    private Object only;
    private Object except;

    private ConfigObject esConfig;

    /**
     * Create closure-based mapping configurator.
     * @param domainClass Grails domain class to be configured
     * @param esConfig ElasticSearch configuration
     */
    ClosureSearchableDomainClassMapper(GrailsDomainClass domainClass, ConfigObject esConfig) {
        this.esConfig = esConfig;
        this.grailsDomainClass = domainClass;
        // fixme: should be only consider persistent properties?
        for(GrailsDomainClassProperty prop : domainClass.getProperties()) {
            this.mappableProperties.add(prop.getName());
        }
    }

    /**
     * @return searchable domain class mapping
     */
    public SearchableClassMapping buildClassMapping() {
        if (grailsDomainClass.hasProperty("searchable")) {
            Object searchable = grailsDomainClass.getPropertyValue("searchable");
            if (searchable instanceof Boolean) {
                return buildDefaultMapping();
            } else if (searchable instanceof Closure) {
                return buildClosureMapping((Closure) searchable);
            } else {
                throw new IllegalArgumentException("'searchable' property has unknown type: " + searchable.getClass());
            }
        } else {
            return null;
        }
    }

    public SearchableClassMapping buildDefaultMapping() {

        Collection<SearchableClassPropertyMapping> mappedProperties = new ArrayList<SearchableClassPropertyMapping>();
        for(GrailsDomainClassProperty property : grailsDomainClass.getProperties()) {
            List<String> defaultExcludedProperties = (List<String>) esConfig.get("defaultExcludedProperties");
            if (defaultExcludedProperties == null || !defaultExcludedProperties.contains(property.getName())) {
                mappedProperties.add(new SearchableClassPropertyMapping(property));
            }
        }
        SearchableClassMapping scm = new SearchableClassMapping(grailsDomainClass, mappedProperties);
        scm.setRoot(true);
        return scm;
    }

    public SearchableClassMapping buildClosureMapping(Closure searchable) {
        assert searchable != null;

        Collection<SearchableClassPropertyMapping> mappedProperties = new ArrayList<SearchableClassPropertyMapping>();
        
        // Build user-defined specific mappings
        Closure closure = (Closure) searchable.clone();
        closure.setDelegate(this);
        closure.call();

        Set<String> propsOnly = convertToSet(only);
        Set<String> propsExcept = convertToSet(except);

        if (!propsOnly.isEmpty() && !propsExcept.isEmpty()) {
            throw new IllegalArgumentException("Both 'only' and 'except' were used in '" + grailsDomainClass.getPropertyName() + "#searchable': provide one or neither but not both");
        }
        if (!propsExcept.isEmpty()) {
            mappableProperties.removeAll(propsExcept);
        }
        if (!propsOnly.isEmpty()) {
            mappableProperties.clear();
            mappableProperties.addAll(propsOnly);
        }
        // Clean out any per-property specs not allowed by 'only','except' rules.
        customMappedProperties.keySet().retainAll(mappableProperties);

        for(String propertyName : mappableProperties) {
            SearchableClassPropertyMapping scpm = customMappedProperties.get(propertyName);
            if (scpm == null) {
                scpm = new SearchableClassPropertyMapping(grailsDomainClass.getPropertyByName(propertyName));
            }
            mappedProperties.add(scpm);
        }

        SearchableClassMapping scm = new SearchableClassMapping(grailsDomainClass, mappedProperties);
        scm.setRoot(true);
        return scm;
    }

    /**
     * Invoked by 'searchable' closure.
     * @param name synthetic method name
     * @param args method arguments.
     * @return <code>null</code>
     */
    public Object invokeMethod(String name, Object args) {
        // Predefined mapping options
        if (CLASS_MAPPING_OPTIONS.contains(name)) {
            if (args == null || ObjectUtils.isEmpty((Object[])args)) {
                throw new IllegalArgumentException(grailsDomainClass.getPropertyName() + " mapping declares " + name + " : found no argument.");
            }
            Field target = ReflectionUtils.findField(this.getClass(), name);
            ReflectionUtils.makeAccessible(target);
            ReflectionUtils.setField(target, this, ((Object[])args)[0]);
            return null;
        }

        // Custom properties mapping options
        GrailsDomainClassProperty property = grailsDomainClass.getPropertyByName(name);
        if (property == null) {
            throw new IllegalArgumentException("Unable to find property [" + name + "] used in [" + grailsDomainClass.getPropertyName() + "}#searchable].");
        }
        if (!mappableProperties.contains(name)) {
            throw new IllegalArgumentException("Unable to map [" + grailsDomainClass.getPropertyName() + "." +
                    property.getName() + "]");
        }

        // Check if we already has mapping for this property.
        SearchableClassPropertyMapping propertyMapping = customMappedProperties.get(name);
        if (propertyMapping == null) {
            propertyMapping = new SearchableClassPropertyMapping(property);
            customMappedProperties.put(name, propertyMapping);
        }
        propertyMapping.addAttributes(((Object[])args)[0]);
        return null;
    }

    private Set<String> convertToSet(Object arg) {
        if (arg == null) {
            return Collections.emptySet();
        } else if (arg instanceof String) {
            return Collections.singleton((String) arg);
        } else if (arg instanceof Object[]) {
            return new HashSet<String>(Arrays.asList((String[]) arg));
        } else if (arg instanceof Collection) {
            //noinspection unchecked
            return new HashSet<String>((Collection<String>) arg);
        } else {
            throw new IllegalArgumentException("Unknown argument: " + arg);
        }
    }
}

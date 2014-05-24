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
import org.codehaus.groovy.grails.commons.*;

import java.lang.reflect.Modifier;
import java.util.*;

class SearchableDomainClassMapper extends GroovyObjectSupport {
    /**
     * Options applied to searchable class itself
     */
    public static final Set<String> CLASS_MAPPING_OPTIONS = new HashSet<String>(Arrays.asList("all", "root", "only", "except", "indexId"));
    /**
     * Searchable property name
     */
    public static final String SEARCHABLE_PROPERTY_NAME = "searchable";

    /**
     * Mapping properties used with 'indexId' to allow for a custom stored key
     */
    public static final String INDEX_ID_PROPERTIES_NAME = "properties";
    public static final String INDEX_ID_SEPARATOR_NAME = "separator";
    public static final Set<String> INDEX_ID_MAPPING_OPTIONS = new HashSet<String>(Arrays.asList(INDEX_ID_PROPERTIES_NAME, INDEX_ID_SEPARATOR_NAME));

    /**
     * Class mapping properties
     */
    private Boolean all = true;
    private Boolean root = true;

    private Set<String> mappableProperties = new HashSet<String>();
    private Map<String, SearchableClassPropertyMapping> customMappedProperties = new HashMap<String, SearchableClassPropertyMapping>();
    private GrailsDomainClass grailsDomainClass;
    private GrailsApplication grailsApplication;
    private Object only;
    private Object except;
    private Object indexId;

    private ConfigObject esConfig;

    /**
     * Create closure-based mapping configurator.
     *
     * @param grailsApplication grails app reference
     * @param domainClass       Grails domain class to be configured
     * @param esConfig          ElasticSearch configuration
     */
    SearchableDomainClassMapper(GrailsApplication grailsApplication, GrailsDomainClass domainClass, ConfigObject esConfig) {
        this.esConfig = esConfig;
        this.grailsDomainClass = domainClass;
        this.grailsApplication = grailsApplication;
    }

    public void setAll(Boolean all) {
        this.all = all;
    }

    public void setRoot(Boolean root) {
        this.root = root;
    }

    public void setOnly(Object only) {
        this.only = only;
    }

    public void setExcept(Object except) {
        this.except = except;
    }

    public void setIndexId(Object indexId) {
        this.indexId = indexId;
    }

    public void root(Boolean rootFlag) {
        this.root = rootFlag;
    }


    public void indexId(Object indexId) {
        this.indexId = indexId;
    }

    /**
     * @return searchable domain class mapping
     */
    public SearchableClassMapping buildClassMapping() {

        if (!grailsDomainClass.hasProperty(SEARCHABLE_PROPERTY_NAME)) {
            return null;
        }
        // Process inheritance.
        List<GrailsDomainClass> superMappings = new ArrayList<GrailsDomainClass>();
        Class<?> currentClass = grailsDomainClass.getClazz();
        superMappings.add(grailsDomainClass);

        while (currentClass != null) {
            currentClass = currentClass.getSuperclass();
            if (currentClass != null && DomainClassArtefactHandler.isDomainClass(currentClass)) {
                GrailsDomainClass superDomainClass = (GrailsDomainClass)
                        grailsApplication.getArtefact(DomainClassArtefactHandler.TYPE, currentClass.getName());

                // If the super class is abstract, it needs peculiar processing
                // The abstract class won't be actually mapped to ES, but the concrete subclasses will have to inherit
                // the searchable mapping options.
                if (superDomainClass == null && Modifier.isAbstract(currentClass.getModifiers())) {
                    // We create a temporary dummy GrailsDomainClass instance for this abstract class
                    superDomainClass = new DefaultGrailsDomainClass(currentClass);
                } else {
                    // If superDomainClass is null & not abstract, then we won't process this class
                    break;
                }

                if (superDomainClass.hasProperty(SEARCHABLE_PROPERTY_NAME) &&
                        superDomainClass.getPropertyValue(SEARCHABLE_PROPERTY_NAME).equals(Boolean.FALSE)) {

                    // hierarchy explicitly terminated. Do not browse any more properties.
                    break;
                }
                superMappings.add(superDomainClass);
                if (superDomainClass.isRoot()) {
                    break;
                }
            }
        }

        Collections.reverse(superMappings);

        // hmm. should we only consider persistent properties?
        for (GrailsDomainClassProperty prop : grailsDomainClass.getPersistentProperties()) {
            this.mappableProperties.add(prop.getName());
        }

        // !!!! Allow explicit identifier indexing ONLY when defined with custom attributes.
        mappableProperties.add(grailsDomainClass.getIdentifier().getName());

        // Process inherited mappings in reverse order.
        for (GrailsDomainClass domainClass : superMappings) {
            if (domainClass.hasProperty(SEARCHABLE_PROPERTY_NAME)) {
                Object searchable = domainClass.getPropertyValue(SEARCHABLE_PROPERTY_NAME);
                if (searchable instanceof Boolean) {
                    buildDefaultMapping(domainClass);
                } else if (searchable instanceof java.util.LinkedHashMap) {
                    Set<String> inheritedProperties = getInheritedProperties(domainClass);
                    buildHashMapMapping((LinkedHashMap) searchable, domainClass, inheritedProperties);
                } else if (searchable instanceof Closure) {
                    Set<String> inheritedProperties = getInheritedProperties(domainClass);
                    buildClosureMapping(domainClass, (Closure) searchable, inheritedProperties);
                } else {
                    throw new IllegalArgumentException("'searchable' property has unknown type: " + searchable.getClass());
                }
            }
        }

        // Populate default settings.
        // Clean out any per-property specs not allowed by 'only','except' rules.

        customMappedProperties.keySet().retainAll(mappableProperties);
        mappableProperties.remove(grailsDomainClass.getIdentifier().getName());

        for (String propertyName : mappableProperties) {
            SearchableClassPropertyMapping scpm = customMappedProperties.get(propertyName);
            if (scpm == null) {
                scpm = new SearchableClassPropertyMapping(grailsDomainClass.getPropertyByName(propertyName));
                customMappedProperties.put(propertyName, scpm);
            }
        }

        SearchableClassMapping scm = new SearchableClassMapping(grailsDomainClass, customMappedProperties.values());
        scm.setRoot(root);

        // Override the default properties to use as _id in the index
        if (indexId != null) {
            Map<String, ?> indexIdMap = buildIndexIdMapping();
            scm.setIdentityProperties((List<String>)indexIdMap.get(INDEX_ID_PROPERTIES_NAME));
            if (indexIdMap.containsKey(INDEX_ID_SEPARATOR_NAME)) {
                scm.setIdentitySeparator(indexIdMap.get(INDEX_ID_SEPARATOR_NAME).toString());
            }
        }

        return scm;
    }

    /**
     * Examines the indexId property, and converts it into a Map keyed by the values in INDEX_ID_MAPPING_OPTIONS
     * @return A Map with the custom indexId definition
     */
    private Map<String, Object> buildIndexIdMapping() {
        if ((indexId != null) && !root) {
            throw new IllegalArgumentException("'indexId' was used on non-root '" + grailsDomainClass.getPropertyName() + "#searchable': indexId may only apply to root searchable classes");
        }

        Object args = indexId;

        Map indexIdMapping = null;
        if (args instanceof String) {
            indexIdMapping = Collections.singletonMap(INDEX_ID_PROPERTIES_NAME, Collections.singletonList(args));
        }
        else if (args instanceof Collection) {
            indexIdMapping = Collections.singletonMap(INDEX_ID_PROPERTIES_NAME, new ArrayList<String>((Collection) args));
        }
        else if (args instanceof Map) {
            indexIdMapping = new HashMap();
            for (Object key : ((Map)args).keySet()) {
                if (!INDEX_ID_MAPPING_OPTIONS.contains(key.toString())) {
                    throw new IllegalArgumentException("'" + key + "' is not a valid attribute for 'indexId'");
                }
                indexIdMapping.put(key.toString(), ((Map)args).get(key));
            }
            if (!indexIdMapping.containsKey(INDEX_ID_PROPERTIES_NAME)) {
                throw new IllegalArgumentException("'indexId' must contain a '" + INDEX_ID_PROPERTIES_NAME + "' attribute");
            }
        }
        else {
            throw new IllegalArgumentException("Unsupported 'indexId' argument: " + args);
        }

        return indexIdMapping;
    }

    private Set<String> getInheritedProperties(GrailsDomainClass domainClass) {
        // check which properties belong to this domain class ONLY
        Set<String> inheritedProperties = new HashSet<String>();
        for (GrailsDomainClassProperty prop : domainClass.getPersistentProperties()) {
            if (GrailsClassUtils.isPropertyInherited(domainClass.getClazz(), prop.getName())) {
                inheritedProperties.add(prop.getName());
            }
        }
        return inheritedProperties;
    }

    public void buildDefaultMapping(GrailsDomainClass grailsDomainClass) {

        for (GrailsDomainClassProperty property : grailsDomainClass.getPersistentProperties()) {
            //noinspection unchecked
            List<String> defaultExcludedProperties = (List<String>) esConfig.get("defaultExcludedProperties");
            if (defaultExcludedProperties == null || !defaultExcludedProperties.contains(property.getName())) {
                customMappedProperties.put(property.getName(), new SearchableClassPropertyMapping(property));
            }
        }
    }

    public void buildDefaultMapping(Class clazz) {

    }

    public void buildClosureMapping(GrailsDomainClass grailsDomainClass, Closure searchable, Set<String> inheritedProperties) {
        assert searchable != null;

        // Build user-defined specific mappings
        Closure closure = (Closure) searchable.clone();
        closure.setDelegate(this);
        closure.call();

        buildMappingFromOnlyExcept(grailsDomainClass, inheritedProperties);
    }

    public void buildHashMapMapping(LinkedHashMap map, GrailsDomainClass domainClass, Set<String> inheritedProperties) {
        // Support old searchable-plugin syntax ([only: ['category', 'title']] or [except: 'createdAt'])
        only = map.containsKey("only") ? map.get("only") : null;
        except = map.containsKey("except") ? map.get("except") : null;
        indexId = map.containsKey("indexId") ? map.get("indexId") : null;
        buildMappingFromOnlyExcept(domainClass, inheritedProperties);
    }

    private void buildMappingFromOnlyExcept(GrailsDomainClass domainClass, Set<String> inheritedProperties) {
        Set<String> propsOnly = convertToSet(only);
        Set<String> propsExcept = convertToSet(except);
        if (!propsOnly.isEmpty() && !propsExcept.isEmpty()) {
            throw new IllegalArgumentException("Both 'only' and 'except' were used in '" + grailsDomainClass.getPropertyName() + "#searchable': provide one or neither but not both");
        }

        Boolean alwaysInheritProperties = (Boolean) esConfig.get("alwaysInheritProperties");
        boolean inherit = alwaysInheritProperties != null && alwaysInheritProperties;

        // Remove all properties that may be in the "except" rule
        if (!propsExcept.isEmpty()) {
            mappableProperties.removeAll(propsExcept);
        }
        // Only keep the properties specified in the "only" rule
        if (!propsOnly.isEmpty()) {
            // If we have inherited properties, we keep them nonetheless
            if (inherit) {
                mappableProperties.retainAll(inheritedProperties);
            } else {
                mappableProperties.clear();
            }
            mappableProperties.addAll(propsOnly);
        }
    }

    /**
     * Invoked by 'searchable' closure.
     *
     * @param name synthetic method name
     * @param args method arguments.
     * @return <code>null</code>
     */
    public Object invokeMethod(String name, Object args) {
        // Predefined mapping options
//        if (CLASS_MAPPING_OPTIONS.contains(name)) {
//            if (args == null || ObjectUtils.isEmpty((Object[])args)) {
//                throw new IllegalArgumentException(grailsDomainClass.getPropertyName() + " mapping declares " + name + " : found no argument.");
//            }
//            Field target = ReflectionUtils.findField(this.getClass(), name);
//            ReflectionUtils.makeAccessible(target);
//            ReflectionUtils.setField(target, this, ((Object[])args)[0]);
//            return null;
//        }

        // Custom properties mapping options
        GrailsDomainClassProperty property = grailsDomainClass.getPropertyByName(name);
        if (property == null) {
            throw new IllegalArgumentException("Unable to find property [" + name + "] used in [" + grailsDomainClass.getPropertyName() + "}#searchable].");
        }
//        if (!mappableProperties.contains(name)) {
//            throw new IllegalArgumentException("Unable to map [" + grailsDomainClass.getPropertyName() + "." +
//                    property.getName() + "]");
//        }

        // Check if we already has mapping for this property.
        SearchableClassPropertyMapping propertyMapping = customMappedProperties.get(name);
        if (propertyMapping == null) {
            propertyMapping = new SearchableClassPropertyMapping(property);
            customMappedProperties.put(name, propertyMapping);
        }
        //noinspection unchecked
        propertyMapping.addAttributes((Map<String, Object>) ((Object[]) args)[0]);
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
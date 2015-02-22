/*
 * Copyright 2002-2015 the original author or authors.
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

/**
 * This {@link groovy.util.ConfigObject} script provides the default plugin configuration.
 *
 * Note : The plugin loaded this file itself and merge it with the GrailsApplication#Config script.
 * Any properties set in the Config.groovy script of your application override those set here.
 * It is not required to add a reference to this file in your Config.groovy
 */

elasticSearch {
    /**
     * Date formats used by the unmarshaller of the JSON responses
     */
    date.formats = ["yyyy-MM-dd'T'HH:mm:ss.S'Z'"]

    /**
     * Hosts for remote ElasticSearch instances.
     * Will only be used with the "transport" client mode.
     * If the client mode is set to "transport" and no hosts are defined, ["localhost", 9300] will be used by default.
     */
    client.hosts = [
            [host: 'localhost', port: 9300]
    ]

    /**
     * Default mapping property exclusions
     *
     * No properties matching the given names will be mapped by default
     * ie, when using "searchable = true"
     *
     * This does not apply for classes using mapping by closure
     */
    defaultExcludedProperties = ['password']

    /**
     * Determines if the plugin should reflect any database save/update/delete automatically
     * on the ES instance. Default to false.
     */
    disableAutoIndex = false

    /**
     * Should the database be indexed at startup.
     *
     * The value may be a boolean true|false.
     * Indexing is always asynchronous (compared to Searchable plugin) and executed after BootStrap.groovy.
     */
    bulkIndexOnStartup = true

    /**
     *  Max number of requests to process at once. Reduce this value if you have memory issue when indexing a big amount of data
     *  at once. If this setting is not specified, 500 will be use by default.
     */
    maxBulkRequest = 500

    /**
     * Should component-mapped properties be unmarshalled. The default is true.
     */
    unmarshallComponents = true

    /**
     * The name of the ElasticSearch mapping configuration property that annotates domain classes. The default is 'searchable'.
     */
    searchableProperty.name = 'searchable'

    /**
     * The strategy to be used in case of a conflict installing mappings
     */
    migration.strategy = 'alias'

    /**
     * Whether to replace existing indices with aliases when there's a conflict and the 'alias' strategy is chosen
     */
    migration.aliasReplacesIndex = true

    /**
     * When set to false, in case of an alias migration, prevents the alias to point to the newly created index
     */
    migration.disableAliasChange = false

    index.numberOfReplicas = 0

    /**
     * Whether to index and search all non excluded transient properties. All explicitly included transients in @only@ will be indexed regardless.
     */
    includeTransients = false
}

environments {
    development {
        /**
         * Possible values : "local", "node", "transport"
         * If set to null, "node" mode is used by default.
         */
        elasticSearch.client.mode = 'local'
    }
    test {
        elasticSearch {
            client.mode = 'local'
            index.store.type = 'memory' // store local node in memory and not on disk
        }
    }
    production {
        elasticSearch.client.mode = 'node'
    }
}
log4j = {
    error 'org.codehaus.groovy.grails',
          'org.springframework',
          'org.hibernate',
          'net.sf.ehcache.hibernate'
    debug 'org.grails.plugins.elasticsearch'
}

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

    disableAutoIndex = false
    index {
        compound_format = true
    }
}

environments {
    development {
        elasticSearch {
            /**
             * Possible values : "local", "node", "transport"
             */
            client.mode = 'local'
            client.transport.sniff = true
            bulkIndexOnStartup = true
        }
    }

    test {
        elasticSearch {
            client.mode = 'local'
            client.transport.sniff = true
            index.store.type = 'memory'
        }
    }

    production {
        elasticSearch.client.mode = 'node'
    }
}

grails.doc.authors = 'Manuarii Stein, Stephane Maldini, Serge P. Nekoval'
grails.doc.license = 'Apache License 2.0'
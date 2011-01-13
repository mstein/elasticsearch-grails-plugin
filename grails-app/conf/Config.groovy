// configuration for plugin testing - will not be included in the plugin zip
log4j = {
  // Example of changing the log pattern for the default console
  // appender:
  //
  //appenders {
  //    console name:'stdout', layout:pattern(conversionPattern: '%c{2} %m%n')
  //}

  error 'org.codehaus.groovy.grails.web.servlet',  //  controllers
          'org.codehaus.groovy.grails.web.pages', //  GSP
          'org.codehaus.groovy.grails.web.sitemesh', //  layouts
          'org.codehaus.groovy.grails.web.mapping.filter', // URL mapping
          'org.codehaus.groovy.grails.web.mapping', // URL mapping
          'org.codehaus.groovy.grails.commons', // core / classloading
          'org.codehaus.groovy.grails.plugins', // plugins
          'org.codehaus.groovy.grails.orm.hibernate', // hibernate integration
          'org.springframework',
          'org.hibernate',
          'net.sf.ehcache.hibernate'

  warn 'org.mortbay.log'

  /*debug 'org.grails.plugins.elasticSearch'*/
  info 'org.grails.plugins.elasticSearch'
}
elasticSearch {
  /**
   * Date formats used by the unmarshaller of the JSON responses
   */
  date.formats = ["yyyy-MM-dd'T'HH:mm:ss'Z'"]

  /**
   * Hosts for remote ElasticSearch instances.
   * Will only be used with the "transport" client mode.
   * If the client mode is set to "transport" and no hosts are defined, ["localhost", 9300] will be used by default.
   */
  client.hosts = [
          [host: 'localhost', port: 9300]
  ]
}

environments {
  development {
    /**
     * Possible values : "local", "node", "transport"
     */
    elasticSearch.client.mode = 'local'
  }
  test {
    elasticSearch.client.mode = 'local'
  }
  production {
    elasticSearch.client.mode = 'node'
  }
}

// The following properties have been added by the Upgrade process...
grails.views.default.codec="none" // none, html, base64
grails.views.gsp.encoding="UTF-8"

grails.doc.authors = 'Manuarii Stein, Stephane Maldini'
grails.doc.license = 'Apache License 2.0'


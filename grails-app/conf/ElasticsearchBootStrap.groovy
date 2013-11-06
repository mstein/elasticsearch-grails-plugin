class ElasticsearchBootStrap {

    def elasticSearchService
    def grailsApplication

    def init = { servletContext ->
        if (grailsApplication.config.elasticSearch?.bulkIndexOnStartup) {
            log.debug "Performing bulk indexing."
            elasticSearchService.index()
        }
    }
}

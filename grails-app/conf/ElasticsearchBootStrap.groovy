class ElasticsearchBootStrap {

    def elasticSearchBooStrapHelper

    def init = { servletContext ->
        elasticSearchBooStrapHelper.bulkIndexOnStartup()
    }
}

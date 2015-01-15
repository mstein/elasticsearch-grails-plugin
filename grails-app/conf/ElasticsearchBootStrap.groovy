class ElasticsearchBootStrap {

    def elasticSearchBootStrapHelper

    def init = { servletContext ->
        elasticSearchBootStrapHelper.bulkIndexOnStartup()
    }
}

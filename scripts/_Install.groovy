elasticSearchHome = System.getenv("ELASTIC_SEARCH_HOME")

if(!elasticSearchHome) {
	println """\
The environment variable ELASTIC_SEARCH_HOME specifying the location of ElasticSearch is not set.
Please make sure this variable is set so that the ElasticSearch plugin scripts function correctly.
"""
}

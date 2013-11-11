includeTargets << grailsScript("_GrailsInit")

target(startElasticSearch: "The description of the script goes here!") {
	def elasticSearchHome = System.getenv("ELASTIC_SEARCH_HOME")
	if (!elasticSearchHome){
		println("Cannot start ElasticSearch service. Please set the env variable 'ELASTIC_SEARCH_HOME'.")
		exit 1
	}

	ant.exec(executable:"${elasticSearchHome}/bin/elasticsearch",osfamily:"unix") {
		arg value:'-f'
	}
	ant.exec(executable:"${elasticSearchHome}/bin/elasticsearch.bat",osfamily:"windows") {
		arg value:'-f'
	}
}

setDefaultTarget(startElasticSearch)

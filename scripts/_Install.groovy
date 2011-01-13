//
// This script is executed by Grails after plugin was installed to project.
// This script is a Gant script so you can use all special variables provided
// by Gant (such as 'baseDir' which points on project base dir). You can
// use 'ant' to access a global instance of AntBuilder
//
// For example you can create directory under project tree:
//
//    ant.mkdir(dir:"${basedir}/grails-app/jobs")
//

elasticSearchHome = System.getenv("ELASTIC_SEARCH_HOME")

if(!elasticSearchHome) {
	println """\
The environment variable ELASTIC_SEARCH_HOME specifying the location of ElasticSearch is not set.
Please make sure this variable is set so that the ElasticSearch plugin scripts function correctly.
"""
}

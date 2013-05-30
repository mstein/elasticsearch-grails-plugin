grails.project.class.dir = "target/classes"
grails.project.test.class.dir = "target/test-classes"
grails.project.test.reports.dir = "target/test-reports"
grails.project.docs.output.dir = 'docs' // for the gh-pages branch

//grails.tomcat.jvmArgs = ["-Xmx1024m","-Xms512m", "-agentpath:C:\\Program Files (x86)\\YourKit Java Profiler 9.0.9\\bin\\win64\\yjpagent.dll=sampling,onexit=snapshot"]

//grails.project.war.file = "target/${appName}-${appVersion}.war"
grails.project.dependency.resolution = {
    // inherit Grails' default dependencies
    inherits("global") {
        // uncomment to disable ehcache
        // excludes 'ehcache'
    }
    log "warn" // log level of Ivy resolver, either 'error', 'warn', 'info', 'debug' or 'verbose'
    repositories {
        grailsPlugins()
        grailsHome()
        grailsCentral()

        // uncomment the below to enable remote dependency resolution
        // from public Maven repositories
        // mavenLocal()
        mavenCentral()
        //mavenRepo "http://snapshots.repository.codehaus.org"
        //mavenRepo "http://repository.codehaus.org"
        //mavenRepo "http://download.java.net/maven/2/"
        //mavenRepo "http://repository.jboss.com/maven2/"
        mavenRepo "http://oss.sonatype.org/content/repositories/releases/"
    }
    dependencies {
        // specify dependencies here under either 'build', 'compile', 'runtime', 'test' or 'provided' scopes eg.
        runtime "org.elasticsearch:elasticsearch:0.20.6"
        runtime "org.elasticsearch:elasticsearch-lang-groovy:1.2.0"
        runtime 'com.spatial4j:spatial4j:0.3'
		test("org.spockframework:spock-grails-support:0.7-groovy-2.0"){
            export = false
        }
    }
    plugins {
		runtime ":hibernate:$grailsVersion"
        build (":release:2.2.1", ":rest-client-builder:1.0.3") {
            export = false
        }
        test(":spock:0.7") {
            export = false
            exclude "spock-grails-support"
        }
    }
}

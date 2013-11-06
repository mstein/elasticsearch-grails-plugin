grails.project.work.dir = 'target'
grails.project.docs.output.dir = 'docs' // for the gh-pages branch

//grails.tomcat.jvmArgs = ["-Xmx1024m","-Xms512m", "-agentpath:C:\\Program Files (x86)\\YourKit Java Profiler 9.0.9\\bin\\win64\\yjpagent.dll=sampling,onexit=snapshot"]

grails.project.dependency.resolution = {

    inherits 'global'
    log 'warn'

    repositories {
        grailsCentral()
        mavenLocal()
        mavenCentral()
//		  mavenRepo "http://oss.sonatype.org/content/repositories/releases/"
    }

    dependencies {
        runtime "org.elasticsearch:elasticsearch:0.20.6"
        runtime "org.elasticsearch:elasticsearch-lang-groovy:1.2.0"
        runtime 'com.spatial4j:spatial4j:0.3'
        test("org.spockframework:spock-grails-support:0.7-groovy-2.0"){
            export = false
        }
    }

    plugins {
        build ':release:2.2.1', ':rest-client-builder:1.0.3', {
            export = false
        }

        runtime ":hibernate:$grailsVersion"

        test(":spock:0.7") {
            export = false
            exclude "spock-grails-support"
        }
    }
}

import org.apache.commons.io.FileUtils

eventAllTestsEnd = {msg ->
    def dataFolder = new File('data')
    if (dataFolder.isDirectory()) {
        println "Cleaning up ElasticSerch data directory"
        FileUtils.deleteDirectory(dataFolder)
    }
}

import org.apache.commons.io.FileUtils

eventTestCaseStart = { name ->
    println '-' * 60
    println "| $name : started"
}

eventTestCaseEnd = { name, err, out ->
    println "\n| $name : finished"
}

eventAllTestsEnd = {msg ->
    def dataFolder = new File('data')
    if (dataFolder.isDirectory()) {
        println "Cleaning up ElasticSerch data directory"
        FileUtils.deleteDirectory(dataFolder)
    }
}

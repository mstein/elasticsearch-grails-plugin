import groovy.text.SimpleTemplateEngine
import groovy.util.XmlSlurper
import org.apache.commons.io.FileUtils

includeTargets << grailsScript("_GrailsEvents")
includeTargets << grailsScript("_GrailsDocs")

USAGE = """
    publish-github [--commit] [--push]

where
    --commit  = Commits changes in the documentation to the 'gh-pages' branch.
                You are left on the 'gh-pages' branch so you can update the
                other files before pushing.
    --push    = Pushes the changes to GitHub and switches back to the original
                branch. Implies --commit.
"""

target(default: "Generates the plugin documentation and makes it available on your gh-pages branch.") {
    depends(parseArguments)

    // Start by finding out which Git branch we are currently on. We
    // do this by executing "git branch", finding the line in its output
    // that starts with a '*', and then extracting the branch name.
    def output = executeGit("branch")

    def m = output =~ /\*\s+(\S+)/
    def branch = ""
    if (m) {
        branch = m[0][1]
        println "Current branch: ${branch}"
    }
    else {
        logError("Unable to find out current branch. Output from 'git branch' was:\n\n  ${output}".toString())
        exit(1)
    }

    // Next stop: find out what the remote name is for the current branch.
    output = executeGit("config branch.${branch}.remote")
    def remote = output.trim()

    // Once we have the name of the remote, we can find out its URL.
    // It's the git protocol URL, so we then convert it to its HTTP
    // address at GitHub.
    output = executeGit("config remote.${remote}.url")
    def remoteUrl = output.trim()
    m = remoteUrl =~ /git@github.com:(\w+)\/(\S+?)\.git/

    // The first group contains the username on GitHub, the second
    // contains the project name.
    if (m) {
        remoteUrl = "http://github.com/${m[0][1]}/${m[0][2]}/".toString()
    }
    else {
        println "[WARN] Not a recognised GitHub URL: ${remoteUrl}"
    }

    // We have to generate the docs on the current branch because the
    // 'docs' target depends on 'compile', which of course requires all
    // the source files.
    def docsDir
    try {
        docsDir = grailsSettings.docsOutputDir
    }
    catch (MissingPropertyException ex) {
        docsDir = new File("${basedir}/docs")
    }
    ant.delete(dir: docsDir.absolutePath)
    docs()

    // To publish the project pages to GitHub, we need to switch to the
    // gh-pages branch. Since the docs may have changed, we need to move
    // them out of the way before the switch and then copy them over the
    // existing files on the gh-pages branch.
    println "Switching to gh-pages branch"
    def tmpDocsDir = new File("${basedir}/docs-${System.currentTimeMillis()}")
    docsDir.renameTo(tmpDocsDir)

    executeGit("checkout gh-pages")

    FileUtils.copyDirectory(tmpDocsDir, docsDir)
    ant.delete(dir: tmpDocsDir.absolutePath)

    // We populate the template layout with some of the plugin's details,
    // such as author and version. We get this information from the XML
    // plugin descriptor.
    def pluginXml = new File("${basedir}/plugin.xml")
    if (!pluginXml.exists()) {
        logError("No plugin.xml file found - have you packaged your plugin yet?")
        exit(1)
    }

    // Process the template layout to include the specific details of
    // this plugin.
    def xml = new XmlSlurper().parse(pluginXml)
    def engine = new SimpleTemplateEngine()
    def tmpl = engine.createTemplate(new File("${basedir}/main.html.tmpl")).make([
            version: xml.@version.text(),
            grailsVersion: xml.@grailsVersion.text(),
            author: xml.author.text(),
            remoteUrl: remoteUrl ])

    // Overwrite any existing template with the new version.
    def layoutsDir = new File("${basedir}/_layouts")
    layoutsDir.mkdirs()

    new File(layoutsDir, "main.html").write(tmpl.toString())

    // Add all new or changed files to git.
    executeGit("add docs _layouts")

    // If the user wants to, also commit and push the changes.
    if (argsMap["commit"] || argsMap["push"]) {
        executeGit(["commit", "-m", "Auto-publication of plugin docs.", "-a"])
    }

    if (argsMap["push"]) {
        executeGit("push")

        // Now we can return to the original branch.
        executeGit("checkout ${branch}")
    }
}

String executeGit(cmd) {
    cmd = cmd instanceof List ? ["git"] + cmd : "git " + cmd

    def process = cmd.execute()
    def error = new StringBuffer()
    def out = new StringBuffer()
    process.waitForProcessOutput(out, error)
    process.waitFor()

    if (process.exitValue() != 0) {
        println error.toString()
        exit(process.exitValue())
    }

    return out.toString()
}
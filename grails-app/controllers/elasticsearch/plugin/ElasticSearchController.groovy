package elasticsearch.plugin

import org.elasticsearch.groovy.node.GNode
import org.elasticsearch.groovy.node.GNodeBuilder
import static org.elasticsearch.groovy.node.GNodeBuilder.*
import org.elasticsearch.groovy.client.GClient
import test.User
import test.Tweet
import grails.converters.JSON

class ElasticSearchController {
  def elasticSearchIndexService

  def index = {
    render(view: 'index', model: [mavaleur: 'tst'])
  }

  def postTweet = {
    /*GNode node = nodeBuilder().node()
    GClient client = node.client
    def arg = params
    println "Trying to index user:" + params.user + ", tweet:" + params.tweet
    def indexResponse = client.index {
      index "usertweet"
      type "tweet"
      id "${arg.user}${arg.tweet}"
      source {
        user = "${arg.user}"
        tweet = "${arg.tweet}"
      }
    }
    println indexResponse.response
    node.close()*/

    User u = User.findByFirstnameAndLastname(params.user?.firstname, params.user?.lastname)
    if (!u) {
      flash.notice = "User not found"
      render(view: 'index')
      return
    }
    Tweet t = new Tweet(message: params.tweet?.message, user: u)
    t.save()
    flash.notice = "Tweet posted"
    render(view: 'index')
  }

  def createUsers = {
    User u = new User(lastname:'DA', firstname:'John')
    User u2 = new User(lastname:'DA', firstname:'Bernardo')
    u.save()
    u2.save()
    flash.notice = "User created"
    render(view: 'index')
  }

  def searchForTweets2 = {
    def tweets = elasticSearchIndexService.search("message:*?").searchResults
    def tweetsMsg = 'Messages : '
    tweets.each {
      tweetsMsg += "<br />Tweet de ${it} : ${it}"
    }
    flash.notice = tweetsMsg
    render(view: 'index')
  }

  def searchForUserTweets = {
    /*GNode node = nodeBuilder().node()
    GClient client = node.client
    def args = params
    def search = client.search {
      indices "usertweet"
      types "tweet"
      source {
        query {
          term(user:"${args.user}")
        }
      }
    }
*/
    /*node.close()*/
    def tweets = Tweet.search("message:*?").searchResults
    def tweetsMsg = 'Messages : '
    tweets.each {
      tweetsMsg += "<br />Tweet de ${it.user} : ${it.message}"
    }
    flash.notice = tweetsMsg
    render(view: 'index')
  }
}

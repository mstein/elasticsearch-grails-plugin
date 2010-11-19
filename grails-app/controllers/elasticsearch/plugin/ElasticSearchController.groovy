package elasticsearch.plugin

import test.User
import test.Tweet

class ElasticSearchController {
  def elasticSearchIndexService

  def index = {
    render(view: 'index')
  }

  def postTweet = {
    User u = User.findByFirstnameAndLastname(params.user?.firstname, params.user?.lastname)
    if (!u) {
      flash.notice = "User not found"
      render(view: 'index')
      return
    }
    Tweet t = new Tweet(message: params.tweet?.message, user: u)
    u.addToTweets(t)
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

  def searchForUserTweets = {
    def tweets = Tweet.search("${params.message.search}").searchResults
    def tweetsMsg = 'Messages : '
    tweets.each {
      tweetsMsg += "<br />Tweet de ${it.user} : ${it.message}"
    }
    flash.notice = tweetsMsg
    render(view: 'index')
  }
}

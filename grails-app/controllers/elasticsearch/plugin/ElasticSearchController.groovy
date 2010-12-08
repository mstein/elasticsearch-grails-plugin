package elasticsearch.plugin

import test.User
import test.Tweet
import grails.converters.JSON
import test.Tag

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
    // Create tweet
    Tweet t = new Tweet(message: params.tweet?.message, user: u)
    // Add tweet to user
    u.addToTweets(t)

    // Resolve tags
    if(params.tags){
      def tagsList = params.tags.split(',')
      tagsList.each {
        def tag = Tag.findByName(it.trim(), [cache:true])
        if(!tag){
          tag = new Tag(name:it.trim(), tweet:t)
        }
        t.addToTags(tag)
      }
    }

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
      tweetsMsg += "<br />Tweet from ${it.user?.firstname} ${it.user?.lastname} : ${it.message} ${it.tags ?: '<em>no tags</em>'}"
      tweetsMsg += "(tags : ${it.tags?.collect{t -> t.name}})"
    }
    flash.notice = tweetsMsg
    render(view: 'index')
  }
}

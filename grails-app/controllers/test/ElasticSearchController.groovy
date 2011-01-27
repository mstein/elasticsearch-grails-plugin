package test

class ElasticSearchController {
  def elasticSearchService
  def testCaseService

  def index = {
    render(view: 'index')
  }

  def postTweet = {
    if(!params.user?.id) {
      flash.notice = "No user selected."
      redirect(action: 'index')
      return
    }
    User u = User.get(params.user.id)
    if (!u) {
      flash.notice = "User not found"
      redirect(action: 'index')
      return
    }
    // Create tweet
    testCaseService.addTweet(params.tweet?.message, u, params.tags)

    flash.notice = "Tweet posted"
    redirect(action: 'index')
  }

  def createUsers = {
    testCaseService.createUsers()
    flash.notice = "User created"
    redirect(action: 'index')
  }

  def searchForUserTweets = {
    def tweets = Tweet.search("${params.message.search}").searchResults
    def tweetsMsg = 'Messages : '
    tweets.each {
      tweetsMsg += "<br />Tweet from ${it.user?.firstname} ${it.user?.lastname} : ${it.message} "
      tweetsMsg += "(tags : ${it.tags?.collect{t -> t.name}})"
    }
    flash.notice = tweetsMsg
    redirect(action: 'index')
  }

  def searchUserTerm = {
      
  }

  def searchAll = {
    def res = elasticSearchService.search("${params.query}").searchResults
    def resMsg = '<strong>Global search result(s):</strong><br />'
    res.each {
      switch(it){
        case Tag:
          resMsg += "<strong>Tag</strong> ${it.name}<br />"
          break
        case Tweet:
          resMsg += "<strong>Tweet</strong> \"${it.message}\" from ${it.user.firstname} ${it.user.lastname}<br />"
          break
        case User:
          def pics = it.photos?.collect { pic -> "<img width=\"40\" height=\"40\" src=\"${pic.url}\"/>" }.join(',')
          resMsg += "<strong>User</strong> ${it.firstname} ${it.lastname} ${it.role} ${pics}<br />"
          break
        case Photo:
          resMsg += "<img width=\"40\" height=\"40\" src=\"${it.url}\"/><br/>"
          break
        default:
          resMsg += "<strong>Other</strong> ${it}<br />"
          break
      }

    }
    flash.notice = resMsg
    redirect(action:'index')
  }

  def updateActivity = {
    if(!params.user?.id) {
      flash.notice = "No user selected."
      redirect(action: 'index')
      return
    }
    User u = User.get(params.user.id)
    if (!u) {
      flash.notice = "User not found"
      redirect(action: 'index')
      return
    }
    testCaseService.updateActivity(u, params.user?.activity)

    flash.notice = "${u.firstname} ${u.lastname}'s activity has been updated."
    redirect(action: 'index')
  }

  def deleteTweet = {
    if(!params.id) {
      flash.notice = "No tweet selected."
      redirect(action: 'index')
      return
    }
    testCaseService.deleteTweet(params.id.toLong())

    flash.notice = "Tweet deleted."
    redirect(action: 'index')
  }
}

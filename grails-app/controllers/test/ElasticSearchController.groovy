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
    def highlighter = {
      field 'message', 0, 0
      preTags '<strong>'
      postTags '</strong>'
    }
    // minimalistic test for Query DSL.
    def result = elasticSearchService.search(highlight:highlighter, searchType:'dfs_query_and_fetch') {
      bool {
          must {
              query_string(query: params.query)
          }
          if (params.firstname) {
              must {
                  term(firstname: params.firstname)
              }
          }
      }
    }
    def highlight = result.highlight
    def resMsg = "<strong>${params.query?.encodeAsHTML()}</strong> search result(s): <strong>${result.total}</strong><br />"
    result.searchResults.eachWithIndex { obj, count ->
        println obj.class.name
      switch(obj){
        case Tag:
          resMsg += "<strong>Tag</strong> ${obj.name.encodeAsHTML()}<br />"
          break
        case Tweet:
          resMsg += "<strong>Tweet</strong> \"${highlight[count].message.fragments?.getAt(0)}\" from ${obj.user.firstname} ${obj.user.lastname}<br />"
          break
        case User:
          def pics = obj.photos?.collect { pic -> "<img width=\"40\" height=\"40\" src=\"${pic.url}\"/>" }.join(',')
          resMsg += "<strong>User</strong> ${obj.firstname} ${obj.lastname} ${obj.role} ${pics}<br />"
          break
        case Photo:
          resMsg += "<img width=\"40\" height=\"40\" src=\"${obj.url}\"/><br/>"
          break
        default:
          resMsg += "<strong>Other</strong> ${obj}<br />"
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

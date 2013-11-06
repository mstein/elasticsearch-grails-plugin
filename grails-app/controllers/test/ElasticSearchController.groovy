package test

class ElasticSearchController {
  def elasticSearchService
  def testCaseService

  def index = {
  }

  def createMassProducts = {
      def maxProducts = params.number ? params.long('number') : 1000l
      def batched = params.batched ? params.boolean('batched') : false
      def persisted = params.persisted ? params.boolean('persisted') : false
      def startDate = new Date()
      def endDate
      if(persisted) {
          testCaseService.createMassProductsPersisted(maxProducts, batched)
      } else {
          testCaseService.createMassProducts(maxProducts, batched)
      }
      endDate = new Date()
      def timeElapsed = (endDate.time - startDate.time) / 1000
      flash.notice = "Created ${maxProducts} products in ${timeElapsed} seconds.[batched:${batched}, persisted:${persisted}]"
      redirect(action: 'index')
  }

  def countExistingProducts = {
      def nbProducts = Product.count()

      flash.notice = "There are ${nbProducts} products in the database."
      redirect(action: 'index')
  }

  def createEvent = {
      try {
          testCaseService.createEvent(params.event?.name, params.event?.name)
          flash.notice = "Created event ${params.event?.name}"
      } catch (e) {
          flash.notice = "Couldn't create event ${params.event?.name} <br /><br />" + e.message
      }

      redirect(action:'index')
  }

  def reindexExistingProduct = {
      def nbProducts = Product.count()
      def startDate = new Date()
      def endDate

      testCaseService.batchIndex()

      endDate = new Date()
      def timeElapsed = (endDate.time - startDate.time) / 1000
      flash.notice = "Reindexed ${nbProducts} products in ${timeElapsed} seconds.[batched:false]"
      redirect(action: 'index')
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
    /*def tweets = Tweet.search(){
        queryString(query: params.message.search)
    }.searchResults*/
    def count = Tweet.countHits("${params.message.search}")
    //def tweets = Tweet.search("${params.message.search}", [sort:"message", order:"DESC"]).searchResults
    def tweets = Tweet.search(sort:"message", order:"DESC") {
          bool {
              must {
                  query_string(query: "${params.message.search}")
              }
          }
      }.searchResults
    def tweetsMsg = "Messages ($count) : "
    tweets.each {
      tweetsMsg += "<br />Tweet from ${it.user?.firstname} ${it.user?.lastname} : ${it.message} "
      tweetsMsg += "(tags : ${it.tags?.collect{t -> t.name}})"
    }
    flash.notice = tweetsMsg
    redirect(action: 'index')
  }

  def searchUserTerm = {
  }

  def manualIndex = {
      def nUser = new User(lastname:'Smith',
            firstname:'John',
            password:'password',
            inheritedProperty: 'that value',
            indexButDoNotSearchOnThis: 'You won\'t reach me')
      def nUser2 = new User(lastname:'Smith2',
            firstname:'John',
            password:'password',
            inheritedProperty: 'that value',
            indexButDoNotSearchOnThis: 'You won\'t reach me')
      nUser.id = 1234
      nUser2.id = 2345
      elasticSearchService.index(nUser, nUser2)

      flash.notice = 'Indexed a transient user.'
      redirect(action:'index')
  }

  def manualIndexAllUser = {
    User.index()
    //elasticSearchService.index(User)
    flash.notice = 'Reindexed all users.'
    redirect(action:'index')
  }

  def searchAll = {
    // Count only
    if(params.count) {
        def result = elasticSearchService.countHits {
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
        def resMsg = "<strong>${params.query?.encodeAsHTML()}</strong> search result(s): <strong>${result}</strong><br />"
        flash.notice = resMsg
        redirect(action:'index')
        return
    }

    // Search
    def highlighter = {
      field 'message'
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
      switch(obj){
        case Tag:
          resMsg += "<strong>Tag</strong> ${obj.name.encodeAsHTML()}<br />"
          break
        case Tweet:
          def fragments = highlight[count].message?.fragments
          resMsg += "<strong>Tweet</strong> \"${fragments?.size() ? fragments[0] : ''}\" from ${obj.user.firstname} ${obj.user.lastname}<br />"
          break
        case User:
          def pics = obj.photos?.collect { pic -> "<img width=\"40\" height=\"40\" src=\"${pic.url}\"/>" }?.join(',') ?: ''
          resMsg += "<strong>User</strong> ${obj.firstname} ${obj.lastname} ${obj.role} ${pics}<br />"
          if(obj.anArray) {
              resMsg += "->${obj.anArray}"
          }
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

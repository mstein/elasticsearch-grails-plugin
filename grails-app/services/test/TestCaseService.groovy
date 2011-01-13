package test

class TestCaseService {
  public createUsers(){
    User u = new User(lastname:'DA', firstname:'John', password:'myPass')
    User u2 = new User(lastname:'DA', firstname:'Bernardo', password:'password')
    u.save()
    u2.save()
  }

  public deleteTweet(Long id){
    def t = Tweet.get(id)
    def user = t.user
    user.removeFromTweets(t)
    t.delete()
  }

  public addTweet(String message, User u, String tags = null){
    Tweet t = new Tweet(message: message, user: u)
    // Add tweet to user
    u.addToTweets(t)

    // Resolve tags
    if(tags){
      def tagsList = tags.split(',')
      tagsList.each {
        def tag = Tag.findByName(it.trim(), [cache:true])
        if(!tag){
          tag = new Tag(name:it.trim(), tweet:t)
        }
        t.addToTags(tag)
      }
    }

    t.save()
  }

  public updateActivity(User u, String activity){
    u.activity = activity
    u.save()
  }
}

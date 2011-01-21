package test

class TestCaseService {
  public createUsers(){
    User u = new User(lastname:'DA', firstname:'John', password:'myPass', inheritedProperty: 'my value')
    User u2 = new User(lastname:'DA', firstname:'Bernardo', password:'password', inheritedProperty: 'another value')
    u.addToPhotos(new Photo(url:'http://farm6.static.flickr.com/5208/5247108096_171f46b1ca.jpg'))
    u2.addToPhotos(new Photo(url:'http://farm6.static.flickr.com/5041/5246505607_a3e85c411e.jpg'))
    u2.addToPhotos(new Photo(url:'http://www.landscape-photo.org.uk/albums/userpics/10001/99/normal_Chicken_hawk.jpg'))
    u.save(failOnError:true)
    u2.save(failOnError:true)
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

package test

class User {
  static searchable = {
    except = 'password'
    lastname boost:20
    firstname boost:15, index:'not_analyzed'
    listOfThings index:'no'
    someThings index:'no'
    tweets component:true
  }

  static constraints = {
    tweets cascade:'all'
  }
  static hasMany = [
          tweets:Tweet
  ]
  static mappedBy = [
          tweets:'user'
  ]

  String lastname
  String firstname
  String password
  String someThings = 'something'
  ArrayList<String> listOfThings = ['this', 'that', 'andthis']
}

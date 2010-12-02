package test

class User {
  static searchable = {
    lastname boost:20
    firstname boost:15
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
  ArrayList<String> listOfThings = ['this', 'that', 'andthis']
}

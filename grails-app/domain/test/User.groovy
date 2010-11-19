package test

class User {
  static searchable = {
    lastname boost:20
    firstname boost:15
  }

  static constraints = {}
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

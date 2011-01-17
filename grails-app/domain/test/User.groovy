package test

class User {
  static searchable = {
    except = 'password'
    lastname boost:20
    firstname boost:15, index:'not_analyzed'
    listOfThings index:'no'
    someThings index:'no'
    tweets component:true
    photos reference:true
    role converter:test.RoleConverter
  }

  static constraints = {
    tweets cascade:'all'
    role nullable:false
  }
  static hasMany = [
          tweets:Tweet,
          photos:Photo
  ]
  static mappedBy = [
          tweets:'user'
  ]

  String lastname
  String firstname
  String password
  String activity = 'Evildoer'
  String someThings = 'something'
  ArrayList<String> listOfThings = ['this', 'that', 'andthis']
  Role role = Role.ORDINARY

  enum Role {
      ORDINARY, ADMIN
  }
}

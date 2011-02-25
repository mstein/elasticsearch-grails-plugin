package test

class User extends SuperUser {
  static searchable = {
    except = 'password'
    lastname boost:20
    firstname boost:15, index:'not_analyzed', excludeFromAll:true
    name index:'analyzed'
    listOfThings index:'not_analyzed', excludeFromAll:true
    someThings index:'no'
    tweets component:true
    photos reference:true
    role converter:test.RoleConverter
    indexButDoNotSearchOnThis index:'no', excludeFromAll:true
  }

  static constraints = {
    tweets cascade:'all'
    role nullable:false
    anArray nullable:true
    photos nullable:true
  }
  static hasMany = [
          tweets:Tweet,
          photos:Photo,
          listOfThings:String
  ]
  static mappedBy = [
          tweets:'user'
  ]

  static mapping = {
          table 'test_user'
  }

  static transients = ['name']


  String lastname
  String firstname
  String password
  String activity = 'Evildoer'
  String someThings = 'something'
  ArrayList<String> listOfThings = ['this is a list of things', 'with that', 'and this']
  Integer[] listOfInt = [1, 2, 3, 4, 5] as Integer[]
  String indexButDoNotSearchOnThis
  String[] anArray
  Role role = Role.ORDINARY

  // synthetic property, not persistent. 
  public String getName() {
      return firstname + ' ' + lastname
  }

  enum Role {
      ORDINARY, ADMIN
  }
}

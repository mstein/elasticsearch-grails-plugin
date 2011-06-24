package test

class Tweet {
  static searchable = {
    message boost:3.0
    tags component:true
    someClass component:true
    user reference:true
  }

  static belongsTo = [
          user:User
  ]

  static hasMany = [
          tags:Tag
  ]

  static constraints = {
    tags nullable:true, cascade:'save, update'
  }

  String message = ''
  Date dateCreated = new Date()
}

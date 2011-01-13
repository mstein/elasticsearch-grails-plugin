package test

class Tweet {
  static searchable = {
    message boost:2.0
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

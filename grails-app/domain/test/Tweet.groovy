package test

class Tweet {
  static searchable = {
    except = ['dateCreated', 'tags', 'user']
    message boost:2.0
  }

  static belongsTo = [
          user:User
  ]

  static hasMany = [
          tags:Tag
  ]

  static mappedBy = [
          tags:'tweet'
  ]

  static constraints = {
    tags nullable:true
  }

  String message = ''
  Date dateCreated = new Date()
}

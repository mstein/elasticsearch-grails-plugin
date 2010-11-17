package test

class Tweet {
  static searchable = true

  static constraints = {}
  static belongsTo = [
          user:User
  ]

  String message = ''
  Date dateCreated = new Date()
}

package test

class Tweet {
  static searchable = true

  static belongsTo = [
          user:User
  ]

  String message = ''
  Date dateCreated = new Date()
}

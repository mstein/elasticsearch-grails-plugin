package test

class Preferences {
  static searchable = {
    avatarUrl boost:20
    signature boost:10
  }

  Boolean showEmail
  Boolean receiveEmailFromAdmin
  Boolean receiveEmailFromUser
  Boolean receiveNewsletter
  String avatarUrl
  String signature
}

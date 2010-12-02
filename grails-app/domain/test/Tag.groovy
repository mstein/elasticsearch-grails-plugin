package test

class Tag {
  static searchable = true
  static belongsTo = [tweet:Tweet]

  String name
  Integer boostValue = 1
}

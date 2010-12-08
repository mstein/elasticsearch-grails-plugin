package test

class Tag {
  static searchable = {
    except=['tweet']
  }
  static belongsTo = [tweet:Tweet]

  String name
  Integer boostValue = 1
}

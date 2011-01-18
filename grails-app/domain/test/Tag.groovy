package test

class Tag {
  static searchable = {
    except=['boostValue']
  }

  String name
  Integer boostValue = 1

  static mapping = {
      table 'test_tag'
  }
}

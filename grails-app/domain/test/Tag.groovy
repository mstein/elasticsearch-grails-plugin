package test

class Tag {
  static searchable = {
    root false
    except=['boostValue']
  }

  String name
  Integer boostValue = 1

  static mapping = {
      table 'test_tag'
  }
}

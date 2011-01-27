package test

// For testing inheritance.
class SuperUser {

    String inheritedProperty

    static constraints = {
        inheritedProperty(nullable:true,size:0..128)
    }

    static searchable = true

    static mapping = {
            table 'test_super_user'
    }

}

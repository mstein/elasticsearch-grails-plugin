package test

class Department {

    String name
    Long numberOfProducts
    Store store

    static constraints = {
        name blank: false
        numberOfProducts nullable: true
    }

    static searchable = {
        store parent: true, component: true
    }

    static mapping = {
        autoImport(false)
    }
}

package test

class Store {

    String name
    String description = "A description of a store"
    String owner = "Owner of the store"

    static searchable = true

    static constraints = {
        name blank: false
        description nullable: true
        owner nullable: false
    }

    static mapping = {
        autoImport(false)
    }

    public String toString() {
        name
    }
}

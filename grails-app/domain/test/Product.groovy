package test

class Product {
    String name
    String description = "A description of a product"
    Float price = 1.00
    Date date

    static searchable = true

    static constraints = {
        name blank: false
        description nullable: true
        price nullable: true
        date nullable: true
    }

    static mapping = {
        autoImport(false)
    }
}

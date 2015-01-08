package test.mapping.migration

class Product {

    String name
    Supplier supplier
    static constraints = {
    }

    static searchable = {
        root true
        supplier component: 'inner'
    }
}

package test.mapping.migration

class Page {

    int number

    List products

    static hasMany = [products:Product]

    static constraints = {
    }

    static searchable = {
        root false
        products component: true
    }
}

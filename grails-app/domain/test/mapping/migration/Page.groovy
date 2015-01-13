package test.mapping.migration

class Page {

    int number

    List products

    static hasMany = [products:Item]

    static constraints = {
    }

    static searchable = {
        root false
        products component: true
    }
}

package test.mapping.migration

class Catalog {

    String company
    String issue
    List pages

    static hasMany = [pages:Page]

    static constraints = {
    }

    static searchable = {
        pages component: 'inner'
    }
}

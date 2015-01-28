package test.mapping.migration

//TODO: Had to rename Product to Item, as it seems there's an issue with domain classes with the same name on different packages
class Item {

    String name
    Supplier supplier
    static constraints = {
        supplier nullable: true
    }

    static searchable = {
        root true
        supplier component: 'inner'
    }
}

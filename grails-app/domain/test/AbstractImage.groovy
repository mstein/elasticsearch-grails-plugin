package test

abstract class AbstractImage {
    static searchable = {
        name index:'not_analyzed'
    }
    String name

    static constraints = {
        index nullable:true
    }
}

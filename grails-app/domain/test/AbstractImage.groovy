package test

abstract class AbstractImage {
    String name

    static constraints = {
        name nullable:true
    }
}

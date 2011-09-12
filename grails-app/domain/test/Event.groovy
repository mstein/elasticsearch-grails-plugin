package test

class Event {
    String id
    String name
    String description
    Integer priority = 0

    static searchable = true

    static mapping = {
        id generator: "uuid"
    }
    static constraints = {
    }
}

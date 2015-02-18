package test

class Color {

    String name
    Integer red
    Integer green
    Integer blue

    static constraints = {
        name blank: false
    }

    static searchable = {
        root false
        only = [ 'name' ]
    }

}

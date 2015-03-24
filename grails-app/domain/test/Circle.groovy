package test

class Circle {

    double radius
    Color color

    static constraints = {
        color nullable: true
    }

    static searchable = {
        color component: 'inner'
    }
}

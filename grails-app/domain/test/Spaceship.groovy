package test

/**
 * @author Noam Y. Tenne
 */
class Spaceship {

    String name
    Person captain

    static searchable = {
        captain component: 'inner'
    }
}

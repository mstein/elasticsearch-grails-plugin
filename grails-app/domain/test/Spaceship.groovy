package test

/**
 * @author Noam Y. Tenne
 */
class Spaceship {

    String name
    Person captain
    String shipData

    static searchable = {
        captain component: 'inner'
        shipData dynamic: true
    }

    static mapping = {
        shipData type: 'text', column: 'data'
    }

    static constraints = {
        shipData nullable: true
    }
}

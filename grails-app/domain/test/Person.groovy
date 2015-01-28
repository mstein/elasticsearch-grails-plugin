package test

/**
 * @author Noam Y. Tenne
 */
class Person {

    String firstName
    String lastName

    List<String> nickNames

    String getFullName() {
        return firstName + " " + lastName
    }

    static transients = ['fullName']
    static hasMany = [nickNames:String]

    static searchable = {
        root false
    }
}

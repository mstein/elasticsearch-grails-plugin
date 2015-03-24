package test

class Department {

    String name
    Long numberOfProducts
    Store store

    String getFullDepartmentName() {
        "${store}'s ${name} department"
    }

    static constraints = {
        name blank: false
        numberOfProducts nullable: true
    }

    static searchable = {
        store parent: true, component: true
    }

    static mapping = {
        autoImport(false)
    }
}

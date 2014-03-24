package test

class Building {

    String name
    GeoPoint location

    static constraints = {
        name(nullable: true)
    }

    static searchable = {
        location geoPoint: true, component: true
    }
}

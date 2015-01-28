package test

class Building {

    String name
	Date date = new Date()
    GeoPoint location

    static constraints = {
        name(nullable: true)
    }

    static searchable = {
        location geoPoint: true, component: true
		date alias: "@timestamp"
    }
}

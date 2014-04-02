package test

import org.json.simple.JSONObject

class Product {
    String name
    String description = "A description of a product"
    Float price = 1.00
    Date date
    JSONObject json

    static searchable = true

    static constraints = {
        name blank: false
        description nullable: true
        price nullable: true
        date nullable: true
        json nullable: true
    }

    static mapping = {
        autoImport(false)
        json type: JsonUserType
    }
}

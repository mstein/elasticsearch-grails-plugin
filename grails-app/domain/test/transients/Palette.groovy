package test.transients

class Palette {

    List<Color> colors

    String getName() {
        "${colors} palette"
    }

    List<String> getComplementaries() {
        colors.collect {
            it.complementary() as String
        }
    }

    static transients = ['complementaries']

    static searchable = {
        only = ['complementaries']
    }

    static hasMany = [colors : Color, tags:String, complementaries: String]

    static constraints = {
    }
}

enum Color {
    cyan, magenta, yellow, red, green, blue

    Color complementary() {
        switch(this) {
            case cyan: return red
            case magenta: return green
            case yellow: return blue
            case red: return cyan
            case green: return magenta
            case blue: return yellow
        }
    }
}

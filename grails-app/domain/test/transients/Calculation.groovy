package test.transients

class Calculation {

    int a
    int b
    int multiplication

    int getAddition() {
        a + b
    }

    int getMultiplication() {
        multiplication ?: (a * b)
    }

    void setMultiplication(int multiplication) {
        this.multiplication = multiplication
    }

    int getDivision() {
        a / b
    }

    static transients = ['addition', 'multiplication', 'division']

    static constraints = {
    }

    static searchable = {
        except = ['a','b','division']
    }
}

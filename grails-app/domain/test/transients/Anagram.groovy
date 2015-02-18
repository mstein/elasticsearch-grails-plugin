package test.transients

/**
 * Created by @marcos-carceles on 28/01/15.
 */
class Anagram {

    String original

    String getReverse() {
        original.reverse()
    }

    int getLength() {
        return original.length()
    }

    Boolean getPalindrome() {
        original == reverse
    }

    static transients = ['reverse', 'length', 'palindrome']

    static searchable = true
}

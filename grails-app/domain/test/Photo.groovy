package test

class Photo extends AbstractImage {

    String url

    static searchable = {
        url index:"not_analyzed"
    }

    String toString() {
        "Photo{id=$id,url='$url}'"
    }
}

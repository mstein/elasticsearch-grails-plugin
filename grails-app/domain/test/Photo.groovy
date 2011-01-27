package test

class Photo {

    String url

    static constraints = {
        url(nullable: false)
    }

    static searchable = true


    public String toString() {
        return "Photo{" +
                "id=" + id +
                ",url='" + url + '\'' +
                '}';
    }
}

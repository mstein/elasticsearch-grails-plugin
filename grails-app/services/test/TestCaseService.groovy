package test

import org.codehaus.groovy.grails.plugins.DomainClassGrailsPlugin

class TestCaseService {
    def sessionFactory
    def elasticSearchService

    def cleanUpGorm() {
        def session = sessionFactory.currentSession
        session.flush()
        session.clear()
        DomainClassGrailsPlugin.PROPERTY_INSTANCE_MAP.get().clear()
    }

    private void createMassProducts(long maxProducts = 1000l, batched = false) {
        def collProd = []
        maxProducts.times {
            def p = new Product()
            p.name = "Product${it}"
            p.id = it
            collProd << p
            if(batched && it % 1000 == 0) {
                elasticSearchService.index(collProd)
                collProd.clear()
            }
        }
        if(!batched) {
            elasticSearchService.index(collProd)
            collProd.clear()
        }
    }

    private void batchIndex() {
        def batchSize = 10000
        def nbProducts = Product.count()
        def nbBatches = nbProducts / batchSize
        nbBatches = nbBatches < 1 ? 1 : nbBatches
        nbBatches.times {
            def products = Product.findAll('from Product as p', [max:batchSize, offset:it*batchSize])
            elasticSearchService.index(products)
        }
    }

    private void testcreateMassProductsPersisted(long maxProducts = 1000l, batched = false) {
        Long startTime = System.currentTimeMillis()
        Long lastTime = startTime
        Long currentTime = startTime
        for(long it = 0; it < maxProducts; it++) {
            if(batched && it % 1000 == 0) {
                currentTime = System.currentTimeMillis()
                println "Saved ${it} instances cleaning up gorm - ${currentTime - lastTime}ms since last - ${currentTime - startTime}ms since beginning"
                lastTime = currentTime
                cleanUpGorm()
            }
            new Product(name: "Product${it}").save(validate:false)
        }
    }

    private void testcreateUsers(){
        User u = new User(lastname:'DA',
            firstname:'John',
            password:'myPass',
            inheritedProperty: 'my value',
            indexButDoNotSearchOnThis: 'alea jacta est',
            anArray:['array', 'of', 'string'])
        User u2 = new User(lastname:'DA',
            firstname:'Bernardo',
            password:'password',
            inheritedProperty: 'another value',
            indexButDoNotSearchOnThis: 'try to search me')
        User u3 = new User(lastname:'Doe',
            firstname:'This is my firstname',
            password:'password',
            inheritedProperty: 'another value again',
            indexButDoNotSearchOnThis: 'Unbelievable value')
        u.addToPhotos(new Photo(name:'myPhoto', url:'http://farm6.static.flickr.com/5208/5247108096_171f46b1ca.jpg'))
        u2.addToPhotos(new Photo(name:'myOtherPhoto', url:'http://farm6.static.flickr.com/5041/5246505607_a3e85c411e.jpg'))
        u2.addToPhotos(new Photo(name:'thatPhoto', url:'http://www.landscape-photo.org.uk/albums/userpics/10001/99/normal_Chicken_hawk.jpg'))
        u.save(failOnError:true)
        u2.save(failOnError:true)
        u3.save(failOnError:true)
    }

    private void testdeleteTweet(Long id){
        def t = Tweet.get(id)
        def user = t.user
        user.removeFromTweets(t)
        t.delete()
    }

    private void testaddTweet(String message, User u, String tags = null){
        Tweet t = new Tweet(message: message, user: u)
        // Add tweet to user
        u.addToTweets(t)

        // Resolve tags
        if(tags){
            def tagsList = tags.split(',')
            tagsList.each {
                def tag = Tag.findByName(it.trim(), [cache:true])
                if(!tag){
                    tag = new Tag(name:it.trim(), tweet:t)
                }
                t.addToTags(tag)
            }
        }

        t.save()
    }

    private void testupdateActivity(User u, String activity){
        u.activity = activity
        u.save()
    }

    private void testcreateEvent(String name, String description) {
        def e = new Event()
        e.name = name ?: 'randomevent'
        e.description = description ?: 'randomdesc'
        if(!e.save()) {
            throw new Exception(e.errors.toString())
        }
    }
}

package org.grails.plugins.elasticsearch

/**
 * @author Noam Y. Tenne
 */
class EntityKey {

    private String entityName
    private Serializable id


    EntityKey(String entityName, Serializable id) {
        this.entityName = entityName
        this.id = id
    }

    boolean equals(o) {
        if (this.is(o)) return true
        if (getClass() != o.class) return false

        EntityKey entityKey = (EntityKey) o

        if (entityName != entityKey.entityName) return false
        if (id != entityKey.id) return false

        return true
    }

    int hashCode() {
        int result
        result = (entityName != null ? entityName.hashCode() : 0)
        result = 31 * result + (id != null ? id.hashCode() : 0)
        return result
    }
}

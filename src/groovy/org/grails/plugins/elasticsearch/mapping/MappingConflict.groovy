package org.grails.plugins.elasticsearch.mapping

/**
 * Created by @marcos-carceles on 26/01/15.
 */
class MappingConflict {

    SearchableClassMapping scm
    Exception exception

    public String toString() {
        "Conflict on ${scm.indexName}/${scm.elasticTypeName}, due to '${exception.message}'"
    }
}

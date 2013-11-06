/*
 * Copyright 2002-2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.plugins.elasticsearch.index

/**
 * @author Noam Y. Tenne
 */
class IndexEntityKey implements Serializable {
    /**
     * stringified id.
     */
    final String id
    final Class clazz

    IndexEntityKey(String id, Class clazz) {
        this.id = id
        this.clazz = clazz
    }

    boolean equals(o) {
        if (is(o)) return true
        if (getClass() != o.getClass()) return false

        IndexEntityKey that = o

        if (clazz != that.clazz) return false
        if (id != that.id) return false

        true
    }

    int hashCode() {
        int result
        result = id.hashCode()
        result = 31 * result + (clazz != null ? clazz.hashCode() : 0)
        result
    }

    @Override
    String toString() {
        "IndexEntityKey{id=$id, clazz=$clazz}"
    }
}

package test

import grails.converters.JSON
import org.codehaus.groovy.grails.web.json.JSONElement
import org.codehaus.groovy.grails.web.json.JSONObject
import org.hibernate.usertype.UserType

import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types

class JsonUserType implements UserType {
    int[] sqlTypes() {
        [Types.LONGVARCHAR] as int[]
    }

    Class returnedClass() {
        JSONObject
    }

    boolean equals(a, b) {
        a == b
    }

    int hashCode(a) {
        a.hashCode()
    }

    Object nullSafeGet(ResultSet resultSet, String[] names, Object owner) {
        String str = resultSet.getString(names[0])
        str ? JSON.parse(str) : null
    }

    void nullSafeSet(PreparedStatement preparedStatement, Object value, int index) {
        if (value == null) {
            preparedStatement.setNull(index, sqlTypes()[0])
        } else {
            JSONElement json = value as JSONElement
            preparedStatement.setString(index, json.toString())
        }
    }

    Object deepCopy(Object o) {
        o
    }

    Serializable disassemble(Object o) {
        o
    }

    Object assemble(Serializable cached, Object owner) {
        cached
    }

    Object replace(Object original, Object target, Object owner) {
        original
    }

    boolean isMutable() {
        false
    }
}

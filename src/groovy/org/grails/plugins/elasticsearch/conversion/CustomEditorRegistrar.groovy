package org.grails.plugins.elasticsearch.conversion

import org.grails.plugins.elasticsearch.conversion.binders.JSONDateBinder
import org.springframework.beans.PropertyEditorRegistrar
import org.springframework.beans.PropertyEditorRegistry

class CustomEditorRegistrar implements PropertyEditorRegistrar {
    def elasticSearchContextHolder
    def grailsApplication

    void registerCustomEditors(PropertyEditorRegistry reg) {
        elasticSearchContextHolder = grailsApplication.mainContext.getBean('elasticSearchContextHolder')
        reg.registerCustomEditor(Date, new JSONDateBinder(elasticSearchContextHolder.config.date.formats as List))
    }
}

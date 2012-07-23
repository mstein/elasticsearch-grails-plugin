package org.grails.plugins.elasticsearch.conversion

import org.springframework.beans.PropertyEditorRegistrar
import org.springframework.beans.PropertyEditorRegistry
import org.grails.plugins.elasticsearch.conversion.binders.JSONDateBinder
import org.codehaus.groovy.grails.commons.ApplicationHolder

class CustomEditorRegistar implements PropertyEditorRegistrar {
  def elasticSearchContextHolder
  def grailsApplication

  void registerCustomEditors(PropertyEditorRegistry reg) {
    elasticSearchContextHolder = grailsApplication.mainContext.getBean('elasticSearchContextHolder')
    reg.registerCustomEditor(Date.class, new JSONDateBinder(elasticSearchContextHolder.config.date.formats as List))
  }

}

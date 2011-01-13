package org.grails.plugins.elasticsearch.conversion.binders

import java.beans.PropertyEditorSupport
import java.text.SimpleDateFormat
import java.text.ParseException

public class JSONDateBinder extends PropertyEditorSupport {

  final List<String> formats

  public JSONDateBinder(List formats) {
    this.formats = Collections.unmodifiableList(formats)
  }

  public void setAsText(String s) throws IllegalArgumentException {
    if (s != null) {
      for(format in formats){
        // Need to create the SimpleDateFormat every time, since it's not thead-safe
        SimpleDateFormat df = new SimpleDateFormat(format)
        try {
          setValue(df.parse(s))
          return
        } catch (ParseException e) {}
      }
    }
  }
}

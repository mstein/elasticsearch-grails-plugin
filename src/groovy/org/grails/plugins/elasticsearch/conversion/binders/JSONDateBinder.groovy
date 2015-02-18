package org.grails.plugins.elasticsearch.conversion.binders

import java.beans.PropertyEditorSupport
import java.text.ParseException
import java.text.SimpleDateFormat

class JSONDateBinder extends PropertyEditorSupport {

    final utcTimeZone = TimeZone.getTimeZone('UTC')
    final List<String> formats

    JSONDateBinder(List formats) {
        this.formats = Collections.unmodifiableList(formats)
    }

    void setAsText(String s) throws IllegalArgumentException {
        if (s == null) {
            return
        }

        int counter = formats.size()
        for(format in formats){
            // Need to create the SimpleDateFormat every time, since it's not thread-safe
            SimpleDateFormat df = new SimpleDateFormat(format)
            df.timeZone = utcTimeZone

            try {
                setValue(df.parse(s))
                return
            } catch (ParseException e) {
                counter--
                //print "Date ${e.message} does not match the pattern ${format}"
                if (counter > 0) {
                    //println "Will try one of the other(s) [${counter}] pattern(s) left"
                } else {
                    println "Date ${s} did not match any of the pattern registered! You may want to add the pattern to the [elasticSearch.date.formats] setting."
                    println "Error : ${e.message}"
                }
            } catch(Throwable t) {
                println "An error has occured ${t.message} ${format}"
            }
        }
    }
}

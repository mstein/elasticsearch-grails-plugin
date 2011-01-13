package org.grails.plugins.elasticsearch.exception

/**
 * @author Graeme Rocher
 */
class IndexException extends RuntimeException {

  IndexException(String s, Throwable throwable) {
    super(s, throwable);
  }

}

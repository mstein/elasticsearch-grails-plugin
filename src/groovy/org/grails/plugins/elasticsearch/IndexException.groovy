package org.grails.plugins.elasticsearch

/**
 * @author Graeme Rocher
 */
class IndexException extends RuntimeException {

  IndexException(String s, Throwable throwable) {
    super(s, throwable);
  }

}

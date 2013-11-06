package org.grails.plugins.elasticsearch.util

import org.hibernate.FlushMode
import org.hibernate.Session
import org.springframework.orm.hibernate3.SessionFactoryUtils
import org.springframework.orm.hibernate3.SessionHolder
import org.springframework.transaction.support.TransactionSynchronizationManager

class ThreadWithSession extends Thread {
  static Thread startWithSession(sessionFactory, persistenceInterceptor, Closure c) {
    def threadClosure = {
      def sessionBound = false
      try {
        sessionBound = bindSession(sessionFactory)
        persistenceInterceptor.init()
        c.delegate = delegate
        c.run()

        final SessionHolder sessionHolder = (SessionHolder) TransactionSynchronizationManager.getResource(sessionFactory)
        if (sessionHolder != null && !FlushMode.MANUAL.equals(sessionHolder.session.flushMode)) {
          sessionHolder.session.flush()
        }
      } finally {
        if (sessionBound) {
          unbindSession(sessionFactory)
        }
        persistenceInterceptor.flush()
        persistenceInterceptor.destroy()
      }
    }
    return ThreadWithSession.start(threadClosure)
  }

  // Bind session to the thread if not available
  private static boolean bindSession(sessionFactory) {
    if (sessionFactory == null) {
      throw new IllegalStateException("SessionFactory not provided.")
    }
    final inStorage = TransactionSynchronizationManager.getResource(sessionFactory)
    if (inStorage != null) {
      ((SessionHolder) inStorage).session.flush()
      return false
    }
    Session session = SessionFactoryUtils.getSession(sessionFactory, true)
    session.setFlushMode(FlushMode.AUTO)
    TransactionSynchronizationManager.bindResource(sessionFactory, new SessionHolder(session))
    return true
  }

  // unbind session & flush it
  private static void unbindSession(sessionFactory) {
    if (sessionFactory == null) {
      throw new IllegalStateException("SessionFactory not provided.")
    }
    SessionHolder sessionHolder = (SessionHolder) TransactionSynchronizationManager.getResource(sessionFactory)
    try {
      if (!FlushMode.MANUAL.equals(sessionHolder.session.flushMode)) {
        sessionHolder.session.flush()
      }
    } finally {
      TransactionSynchronizationManager.unbindResource(sessionFactory)
      SessionFactoryUtils.releaseSession(sessionHolder.session,sessionFactory)
    }
  }
}

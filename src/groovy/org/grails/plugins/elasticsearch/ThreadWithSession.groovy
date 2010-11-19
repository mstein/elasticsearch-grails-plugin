package org.grails.plugins.elasticsearch

import org.springframework.orm.hibernate3.SessionFactoryUtils
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.hibernate.FlushMode
import org.springframework.orm.hibernate3.SessionHolder
import org.hibernate.Session

class ThreadWithSession extends Thread {
  public static Thread startWithSession(sessionFactory, Closure c) {
    def sessionBound = false
    def threadClosure = {
      try {
        sessionBound = bindSession(sessionFactory)
        delegate = c.delegate
        c()

        final SessionHolder sessionHolder = (SessionHolder) TransactionSynchronizationManager.getResource(sessionFactory)
        if (sessionHolder != null && !FlushMode.MANUAL.equals(sessionHolder.session.flushMode)) {
          sessionHolder.session.flush()
        }
      } catch (Exception e) {
        throw e
      } finally {
        if (sessionBound) {
          unbindSession(sessionFactory)
        }
      }

    }

    return ThreadWithSession.start(threadClosure)
  }

  // Bind session to the thread if not available

  private static boolean bindSession(sessionFactory) {
    if (sessionFactory == null) {
      throw new IllegalStateException("SessionFactory not provided.")
    }
    final Object inStorage = TransactionSynchronizationManager.getResource(sessionFactory)
    if (inStorage != null) {
      ((SessionHolder) inStorage).session.flush()
      return false;
    } else {
      Session session = SessionFactoryUtils.getSession(sessionFactory, true)
      session.setFlushMode(FlushMode.AUTO)
      TransactionSynchronizationManager.bindResource(sessionFactory, new SessionHolder(session))
      return true;
    }
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
    } catch (Exception e) {
      throw e
    } finally {
      TransactionSynchronizationManager.unbindResource(sessionFactory)
      SessionFactoryUtils.closeSession(sessionHolder.session)
    }
  }
}

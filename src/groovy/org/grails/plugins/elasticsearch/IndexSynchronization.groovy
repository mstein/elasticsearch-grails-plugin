package org.grails.plugins.elasticsearch

import org.grails.plugins.elasticsearch.index.IndexRequestQueue
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.transaction.support.TransactionSynchronizationAdapter

/**
 * @author Noam Y. Tenne
 */
class IndexSynchronization extends TransactionSynchronizationAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(this)

    private IndexRequestQueue indexRequestQueue
    private AuditEventListener auditEventListener

    IndexSynchronization(IndexRequestQueue indexRequestQueue, AuditEventListener auditEventListener) {
        this.indexRequestQueue = indexRequestQueue
        this.auditEventListener = auditEventListener
    }

    /**
     * Fired on transaction completion (commit or rollback).
     * @param status transaction completion status
     */
    void afterCompletion(int status) {
        def objsToIndex = auditEventListener.getPendingObjects()
        def objsToDelete = auditEventListener.getDeletedObjects()
        switch (status) {
            case STATUS_COMMITTED:
                if (objsToIndex && objsToDelete) {
                    objsToIndex.keySet().removeAll(objsToDelete.keySet())
                }

                if (objsToIndex) {
                    for (obj in objsToIndex.values()) {
                        indexRequestQueue.addIndexRequest(obj)
                    }
                }
                if (objsToDelete) {
                    for (obj in objsToDelete.values()) {
                        indexRequestQueue.addDeleteRequest(obj)
                    }
                }

                // flush to index.
                indexRequestQueue.executeRequests()

                break
            case STATUS_ROLLED_BACK:
                LOG.debug "Rollbacking ${objsToIndex ? objsToIndex.size() : 0} objs."
                break
            default:
                LOG.error 'Unknown transaction state.'
        }

        // Clear objs
        auditEventListener.clearPendingObjects()
        auditEventListener.clearDeletedObjects()
    }
}

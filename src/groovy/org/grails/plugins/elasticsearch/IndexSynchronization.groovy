package org.grails.plugins.elasticsearch

import org.apache.log4j.Logger
import org.grails.plugins.elasticsearch.index.IndexRequestQueue
import org.springframework.transaction.support.TransactionSynchronizationAdapter

/**
 * @author Noam Y. Tenne
 */
class IndexSynchronization extends TransactionSynchronizationAdapter {

    private static final Logger LOG = Logger.getLogger(IndexSynchronization)

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
    def void afterCompletion(int status) {
        def objsToIndex = auditEventListener.getPendingObjects()
        def objsToDelete = auditEventListener.getDeletedObjects()
        switch (status) {
            case STATUS_COMMITTED:
                if (objsToIndex && objsToDelete) {
                    objsToIndex.keySet().removeAll(objsToDelete.keySet())
                }

                if (objsToIndex) {
                    for (def obj : objsToIndex.values()) {
                        indexRequestQueue.addIndexRequest(obj)
                    }
                }
                if (objsToDelete) {
                    for (def obj : objsToDelete.values()) {
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

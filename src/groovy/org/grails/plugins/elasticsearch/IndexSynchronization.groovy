package org.grails.plugins.elasticsearch

import org.grails.plugins.elasticsearch.index.IndexRequestQueue
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.transaction.support.TransactionSynchronizationAdapter
import org.springframework.transaction.support.TransactionSynchronizationManager

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
                def resourceMap = TransactionSynchronizationManager.resourceMap
                try {
                    indexRequestQueue.executeRequests()
                } finally {
                    def resourceMapAfterOp = TransactionSynchronizationManager.resourceMap
                    /**
                     * If we've got a difference between the resource maps before and after the request execution phase,
                     * it means that newly added resources were bound to the thread
                     */
                    if (((resourceMapAfterOp != null) && (resourceMap != null)) &&
                            (resourceMapAfterOp != resourceMap)) {
                        /**
                         * Check the difference between the keys of the resource maps before and after the request execution
                         * to find out which keys must be unbound
                         */
                        def keysBeforeOp = resourceMap.keySet()
                        def addedKeys = resourceMapAfterOp.keySet().findAll { !keysBeforeOp.contains(it) }
                        addedKeys.each { TransactionSynchronizationManager.unbindResource(it) }
                    }
                }

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

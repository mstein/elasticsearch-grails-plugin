package org.grails.plugins.elasticsearch

import org.grails.plugins.elasticsearch.util.ElasticSearchUtils

class IndexRequestBuffer {
  /**
   * A set containing the pending index requests.
   * The constructor is initializing it as a synchronized set.
   */
  Set indexRequests
  /**
   * A set containing the pending delete requests.
   * The constructor is initializing it as a synchronized set.
   */
  Set deleteRequests

  /**
   * No-args constructor, initialize indexRequests & deleteRequests with synchronized set, since the buffer may be
   * used by multiple threads.
   * @return
   */
  public IndexRequestBuffer(){
    indexRequests = Collections.synchronizedSet([] as Set)
    deleteRequests = Collections.synchronizedSet([] as Set)
  }

  def addIndexRequest(domainInstance){
    indexRequests << domainInstance
  }

  def addDeleteRequest(domainInstance){
    deleteRequests << domainInstance
  }
  /**
   * Execute pending requests and clear both index & delete pending queues.
   * @return
   */
  void executeRequests(){
    // If there are domain instances that are both in the index requests & delete requests list,
    // they are directly deleted.
    def notToIndex = indexRequests.intersect(deleteRequests)
    indexRequests.removeAll(notToIndex)

    // Execute index requests
    synchronized (indexRequests){
      indexRequests.each {
        ElasticSearchUtils.indexDomain(it)
      }
      indexRequests.clear()
    }

    // Execute delete requests
    synchronized (deleteRequests){
      deleteRequests.each {
        ElasticSearchUtils.deleteDomain(it)
      }
      deleteRequests.clear()
    }
  }

  /**
   * Clear both index & delete pending queues
   * @return
   */
  def clearAll(){
    synchronized(indexRequests) {
      indexRequests.clear()
    }
    synchronized(deleteRequests) {
      deleteRequests.clear()
    }
  }
}

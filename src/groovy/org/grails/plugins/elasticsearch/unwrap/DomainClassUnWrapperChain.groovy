package org.grails.plugins.elasticsearch.unwrap

import org.springframework.beans.factory.annotation.Autowired

/**
 * @author Noam Y. Tenne.
 */
class DomainClassUnWrapperChain {

    @Autowired(required = false)
    List<DomainClassUnWrapper> unwrappers

    def unwrap(object) {
        if (unwrappers) {
            def unWrapped = unwrappers.findResult { DomainClassUnWrapper unWrapper -> unWrapper.unWrap(object) }
            if (unWrapped) {
                return unWrapped
            }
        }

        object
    }
}

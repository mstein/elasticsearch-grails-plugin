package org.grails.plugins.elasticsearch.unwrap

/**
 * @author Noam Y. Tenne.
 */
class HibernateProxyUnWrapper implements DomainClassUnWrapper {

    @Override
    def unWrap(Object object) {
        return org.codehaus.groovy.grails.orm.hibernate.cfg.GrailsHibernateUtil.unwrapIfProxy(object)
    }
}

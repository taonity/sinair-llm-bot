package org.taonity.sinairllmbot.observability.logging

import org.hibernate.boot.Metadata
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.event.service.spi.EventListenerRegistry
import org.hibernate.event.spi.EventType
import org.hibernate.integrator.spi.Integrator
import org.hibernate.service.spi.SessionFactoryServiceRegistry

class EntityChangeLoggingIntegrator : Integrator {

    override fun integrate(
        metadata: Metadata,
        bootstrapContext: org.hibernate.boot.spi.BootstrapContext,
        sessionFactory: SessionFactoryImplementor
    ) {
        val registry = sessionFactory.serviceRegistry.getService(EventListenerRegistry::class.java)
            ?: return

        val listener = EntityChangeLoggingListener()

        registry.appendListeners(EventType.POST_INSERT, listener)
        registry.appendListeners(EventType.POST_UPDATE, listener)
        registry.appendListeners(EventType.POST_DELETE, listener)
    }

    override fun disintegrate(sessionFactory: SessionFactoryImplementor, serviceRegistry: SessionFactoryServiceRegistry) {
        // nothing to clean up
    }
}

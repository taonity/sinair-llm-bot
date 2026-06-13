package org.taonity.sinairllmbot.observability.logging

import io.github.oshai.kotlinlogging.KotlinLogging
import org.hibernate.event.spi.PostDeleteEvent
import org.hibernate.event.spi.PostDeleteEventListener
import org.hibernate.event.spi.PostInsertEvent
import org.hibernate.event.spi.PostInsertEventListener
import org.hibernate.event.spi.PostUpdateEvent
import org.hibernate.event.spi.PostUpdateEventListener
import org.hibernate.persister.entity.EntityPersister

class EntityChangeLoggingListener : PostInsertEventListener, PostUpdateEventListener, PostDeleteEventListener {

    companion object {
        private val LOGGER = KotlinLogging.logger {}
    }

    override fun onPostInsert(event: PostInsertEvent) {
        LOGGER.debug {
            "Entity INSERTED: ${event.entity::class.simpleName} | id=${event.id} | " +
                    formatState(event.state, event.persister.propertyNames)
        }
    }

    override fun onPostUpdate(event: PostUpdateEvent) {
        val dirtyFields = event.dirtyProperties
        if (dirtyFields != null && dirtyFields.isNotEmpty()) {
            val changes = dirtyFields.joinToString(", ") { idx ->
                val name = event.persister.propertyNames[idx]
                val oldVal = event.oldState?.get(idx)
                val newVal = event.state[idx]
                "$name: '$oldVal' → '$newVal'"
            }
            LOGGER.debug {
                "Entity UPDATED: ${event.entity::class.simpleName} | id=${event.id} | changes=[$changes]"
            }
        } else {
            LOGGER.debug {
                "Entity UPDATED: ${event.entity::class.simpleName} | id=${event.id} | (no dirty properties detected)"
            }
        }
    }

    override fun onPostDelete(event: PostDeleteEvent) {
        LOGGER.debug {
            "Entity DELETED: ${event.entity::class.simpleName} | id=${event.id}"
        }
    }

    override fun requiresPostCommitHandling(persister: EntityPersister): Boolean = false

    private fun formatState(state: Array<out Any?>, propertyNames: Array<out String>): String {
        return propertyNames.zip(state.toList())
            .joinToString(", ") { (name, value) -> "$name='$value'" }
    }
}

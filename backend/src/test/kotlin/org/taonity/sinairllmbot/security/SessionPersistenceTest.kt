package org.taonity.sinairllmbot.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.session.SessionRepository
import org.springframework.session.jdbc.JdbcIndexedSessionRepository
import org.taonity.sinairllmbot.other.ControllerTestsBaseClass

class SessionPersistenceTest : ControllerTestsBaseClass() {

    @Autowired
    lateinit var sessionRepository: SessionRepository<*>

    @Test
    fun `session repository is jdbc-backed`() {
        assertThat(sessionRepository).isInstanceOf(JdbcIndexedSessionRepository::class.java)
    }
}

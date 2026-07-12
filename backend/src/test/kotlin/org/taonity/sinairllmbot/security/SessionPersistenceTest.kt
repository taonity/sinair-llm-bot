package org.taonity.sinairllmbot.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.session.SessionRepository
import org.springframework.session.jdbc.JdbcIndexedSessionRepository
import org.taonity.sinairllmbot.other.ControllerTestsBaseClass

/**
 * HTTP sessions must be persisted via Spring Session JDBC so console logins survive a backend
 * restart. Under Spring Boot 4 this auto-configuration only activates when the
 * spring-boot-session-jdbc module is on the classpath; otherwise the SessionRepository is absent
 * and sessions live in Tomcat's in-memory manager (never written to the DB, lost on restart).
 */
class SessionPersistenceTest : ControllerTestsBaseClass() {

    @Autowired
    lateinit var sessionRepository: SessionRepository<*>

    @Test
    fun `session repository is jdbc-backed`() {
        assertThat(sessionRepository).isInstanceOf(JdbcIndexedSessionRepository::class.java)
    }
}

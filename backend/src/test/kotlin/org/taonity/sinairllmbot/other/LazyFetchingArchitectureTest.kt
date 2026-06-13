package org.taonity.sinairllmbot.other

import jakarta.persistence.EntityManager
import net.ttddyy.dsproxy.QueryCountHolder
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.jdbc.Sql
import org.taonity.sinairllmbot.user.repository.UserRepository

@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DataJpaTest
@Sql(scripts = ["classpath:sql/clear-data.sql", "classpath:sql/test-data.sql"])
@Sql(scripts = ["classpath:sql/clear-data.sql"], executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
@ActiveProfiles("datasource-proxy", "h2")
class LazyFetchingArchitectureTest {

    @Autowired
    lateinit var entityManager: EntityManager
    @Autowired
    lateinit var userRepository: UserRepository

    @TestFactory
    fun `findById executes exactly one select`(): List<DynamicTest> {
        val cases = listOf(
            "UserEntity" to { userRepository.findById("test-google-id") },
        )

        return cases.map { (entityName, repoCall) ->
            DynamicTest.dynamicTest(entityName) {
                entityManager.flush()
                entityManager.clear()
                val selectsBefore = QueryCountHolder.getGrandTotal().select
                repoCall()
                Assertions.assertThat(QueryCountHolder.getGrandTotal().select - selectsBefore)
                    .`as`("$entityName should be fetched with a single SELECT")
                    .isEqualTo(1)
            }
        }
    }
}

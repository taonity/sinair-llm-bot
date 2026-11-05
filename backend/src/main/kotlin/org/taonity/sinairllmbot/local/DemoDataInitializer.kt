package org.taonity.sinairllmbot.local

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.core.io.ClassPathResource
import org.springframework.core.annotation.Order
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.springframework.stereotype.Component
import javax.sql.DataSource

@Component
@Profile("demo")
@Order(0)
class DemoDataInitializer(
    private val dataSource: DataSource,
    @Qualifier("googleWireMockServer") private val google: WireMockServer,
) : ApplicationRunner {
    private companion object {
        private val LOGGER = KotlinLogging.logger {}
    }

    override fun run(args: ApplicationArguments) {
        dataSource.connection.use { connection ->
            require(connection.metaData.url == "jdbc:h2:mem:sinair-demo") {
                "Demo fixtures require the isolated in-memory demo database"
            }
        }
        ResourceDatabasePopulator(ClassPathResource("demo/data.sql")).execute(dataSource)
        google.stubFor(
            get(urlEqualTo("/oauth2/v3/userinfo")).atPriority(1).willReturn(
                aResponse().withHeader("Content-Type", "application/json").withBody(
                    """{"sub":"demo-alice","name":"Alice Morgan","given_name":"Alice","family_name":"Morgan","email":"alice.morgan@example.com","email_verified":true}"""
                )
            )
        )
        LOGGER.info { "English demo fixtures loaded into the isolated demo database" }
    }
}

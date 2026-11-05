package org.taonity.sinairllmbot.local

import com.github.tomakehurst.wiremock.WireMockServer
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.boot.DefaultApplicationArguments
import java.sql.Connection
import java.sql.DatabaseMetaData
import javax.sql.DataSource

class DemoDataInitializerTest {
    @ParameterizedTest
    @ValueSource(strings = ["jdbc:postgresql://localhost/production", "jdbc:h2:file:./h2_db", "jdbc:h2:mem:test"])
    fun `refuses to seed any database except the isolated demo database`(url: String) {
        val metadata = mock(DatabaseMetaData::class.java)
        val connection = mock(Connection::class.java)
        val dataSource = mock(DataSource::class.java)
        val google = mock(WireMockServer::class.java)
        `when`(metadata.url).thenReturn(url)
        `when`(connection.metaData).thenReturn(metadata)
        `when`(dataSource.connection).thenReturn(connection)

        assertThrows(IllegalArgumentException::class.java) {
            DemoDataInitializer(dataSource, google).run(DefaultApplicationArguments())
        }

        verify(connection).close()
        verify(dataSource).connection
        verifyNoInteractions(google)
    }
}

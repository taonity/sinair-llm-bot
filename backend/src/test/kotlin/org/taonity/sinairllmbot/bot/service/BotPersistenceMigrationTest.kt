package org.taonity.sinairllmbot.bot.service

import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.util.UUID

class BotPersistenceMigrationTest {
    @Test
    fun `migrations support retained inbox and deletion independent summary watermark`() {
        val root = if (Files.exists(Path.of("templates"))) Path.of("") else Path.of("..")
        val url = "jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
        Flyway.configure().dataSource(url, "sa", "")
            .locations("filesystem:${root.resolve("templates/docker/flyway/sql/tables").toAbsolutePath()}")
            .load().migrate()
        DriverManager.getConnection(url, "sa", "").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("INSERT INTO chat_message(id,dedup_key,room_target,sender_member_id,sender_login,message_text,message_style,sent_at,received_at) VALUES('old','old','#room',1,'user','old','message',TIMESTAMP '2026-01-01 00:00:00',TIMESTAMP '2026-01-01 00:00:00'),('new','new','#room',1,'user','new','message',TIMESTAMP '2026-01-02 00:00:00',TIMESTAMP '2026-01-02 00:00:00')")
                statement.execute("INSERT INTO pending_bot_message VALUES('new','#room',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")
                statement.execute("DELETE FROM chat_message WHERE id='old'")
                statement.executeQuery("SELECT COUNT(*) FROM chat_message WHERE received_at > TIMESTAMP '2026-01-01 00:00:00'").use {
                    it.next()
                    assertThat(it.getInt(1)).isEqualTo(1)
                }
                statement.execute("DELETE FROM chat_message WHERE id='new'")
                statement.executeQuery("SELECT COUNT(*) FROM pending_bot_message").use {
                    it.next()
                    assertThat(it.getInt(1)).isZero()
                }
            }
        }
    }
}
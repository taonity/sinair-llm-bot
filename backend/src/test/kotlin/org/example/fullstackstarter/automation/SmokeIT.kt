package org.example.fullstackstarter.automation

import tools.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.exists

@Tag("smoke")
@EnabledIfSystemProperty(named = "smoke.tests.enabled", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SmokeIT {

    private lateinit var composeProject: DockerComposeProject

    @BeforeAll
    fun startComposeProject() {
        composeProject = DockerComposeProject(locateRepositoryRoot().resolve("templates/docker"))
        composeProject.start()
    }

    @AfterAll
    fun stopComposeProject() {
        if (::composeProject.isInitialized) {
            composeProject.stop()
        }
    }

    @Test
    fun `all docker compose services are ready`() {
        composeProject.assertServiceHealthy("db")
        composeProject.assertServiceCompletedSuccessfully("flyway")
        composeProject.assertServiceHealthy("app")
        composeProject.assertServiceHealthy("frontend")
    }

    private fun locateRepositoryRoot(): Path {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        while (true) {
            val composeFile = current.resolve("templates/docker/docker-compose.yml")
            if (composeFile.exists()) {
                return current
            }
            val parent = current.parent ?: break
            current = parent
        }
        error("Could not locate repository root from ${System.getProperty("user.dir")}")
    }
}

private class DockerComposeProject(
    private val composeDirectory: Path,
    private val startupTimeout: Duration = Duration.ofMinutes(5)
) {

    private val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
    private val objectMapper = jacksonObjectMapper()
    private val sharedNetworks = listOf("fullstack-starter-shared")
    private var logFollower: Process? = null
    private var logFollowerThread: Thread? = null

    fun start() {
        ensureSharedNetworks()
        compose("down", "-v", "--remove-orphans", allowFailure = true)
        compose("up", "-d", "--wait", "--wait-timeout", startupTimeout.seconds.toString())
        startLogStreaming()
    }

    fun stop() {
        stopLogStreaming()
        compose("down", "-v", "--remove-orphans", allowFailure = true)
    }

    fun assertServiceHealthy(serviceName: String) {
        val state = inspectService(serviceName)
        assertThat(state.status)
            .describedAs("Expected %s container to be running", serviceName)
            .isEqualTo("running")
        assertThat(state.healthStatus)
            .describedAs("Expected %s container to be healthy", serviceName)
            .isEqualTo("healthy")
    }

    fun assertServiceCompletedSuccessfully(serviceName: String) {
        val state = inspectService(serviceName)
        assertThat(state.status)
            .describedAs("Expected %s container to have exited", serviceName)
            .isEqualTo("exited")
        assertThat(state.exitCode)
            .describedAs("Expected %s container to exit successfully", serviceName)
            .isEqualTo(0)
    }

    private fun ensureSharedNetworks() {
        sharedNetworks.forEach { networkName ->
            val inspectResult = docker("network", "inspect", networkName, allowFailure = true)
            if (inspectResult.exitCode != 0) {
                docker("network", "create", networkName)
            }
        }
    }

    private fun inspectService(serviceName: String): ContainerState {
        val containerId = compose("ps", "--all", "-q", serviceName).trim()
        check(containerId.isNotBlank()) { "No container found for service $serviceName" }

        val inspectJson = docker("inspect", containerId).output
        val stateNode = objectMapper.readTree(inspectJson)
            .firstOrNull()
            ?.path("State")
            ?: error("Missing docker inspect state for service $serviceName")

        return ContainerState(
            status = stateNode.path("Status").asText(),
            healthStatus = stateNode.path("Health").path("Status").takeUnless { it.isMissingNode }?.asText(),
            exitCode = stateNode.path("ExitCode").takeUnless { it.isMissingNode }?.asInt()
        )
    }

    private fun compose(vararg args: String, allowFailure: Boolean = false): String {
        return runCommand(
            listOf(
                "docker",
                "compose",
                "--env-file",
                ".env.test",
                "-f",
                "docker-compose.yml"
            ) + args,
            allowFailure
        ).output
    }

    private fun startLogStreaming() {
        stopLogStreaming()

        val process = ProcessBuilder(
            toProcessCommand(
                listOf(
                    "docker",
                    "compose",
                    "--env-file",
                    ".env.test",
                    "-f",
                    "docker-compose.yml",
                    "logs",
                    "--follow",
                    "--no-color"
                ),
                composeDirectory
            )
        )
            .directory(composeDirectory.toFile())
            .redirectErrorStream(true)
            .start()

        val outputThread = Thread {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    println("[compose] $line")
                }
            }
        }.apply {
            name = "docker-compose-log-follower"
            isDaemon = true
            start()
        }

        logFollower = process
        logFollowerThread = outputThread
    }

    private fun stopLogStreaming() {
        logFollower?.destroy()
        logFollower?.waitFor()
        logFollower = null

        logFollowerThread?.join(2_000)
        logFollowerThread = null
    }

    private fun docker(vararg args: String, allowFailure: Boolean = false): CommandResult {
        return runCommand(listOf("docker") + args, allowFailure, composeDirectory.parent)
    }

    private fun runCommand(
        command: List<String>,
        allowFailure: Boolean,
        workingDirectory: Path = composeDirectory
    ): CommandResult {
        val process = ProcessBuilder(toProcessCommand(command, workingDirectory))
            .directory(workingDirectory.toFile())
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        val exitCode = process.waitFor()
        if (exitCode != 0 && !allowFailure) {
            throw AssertionError(
                buildString {
                    appendLine("Command failed with exit code $exitCode")
                    appendLine(command.joinToString(" "))
                    if (output.isNotBlank()) {
                        appendLine(output)
                    }
                }
            )
        }
        return CommandResult(exitCode, output)
    }

    private fun toProcessCommand(command: List<String>, workingDirectory: Path): List<String> {
        if (!isWindows) {
            return command
        }

        val shellCommand = buildString {
            append("cd ")
            append(shellQuote(toWslPath(workingDirectory)))
            append(" && ")
            append(command.joinToString(" ") { shellQuote(it) })
        }

        return listOf("wsl.exe", "sh", "-lc", shellCommand)
    }

    private fun toWslPath(path: Path): String {
        val normalizedPath = path.toAbsolutePath().normalize().toString()
        val driveSeparatorIndex = normalizedPath.indexOf(':')
        if (driveSeparatorIndex <= 0) {
            return normalizedPath.replace('\\', '/')
        }

        val driveLetter = normalizedPath.substring(0, driveSeparatorIndex).lowercase()
        val remainder = normalizedPath.substring(driveSeparatorIndex + 1).replace('\\', '/')
        return "/mnt/$driveLetter$remainder"
    }

    private fun shellQuote(value: String): String {
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }
}

private data class CommandResult(
    val exitCode: Int,
    val output: String
)

private data class ContainerState(
    val status: String,
    val healthStatus: String?,
    val exitCode: Int?
)

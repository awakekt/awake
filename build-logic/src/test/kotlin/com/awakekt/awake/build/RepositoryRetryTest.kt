/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build

import com.sun.net.httpserver.HttpServer
import org.gradle.testkit.runner.GradleRunner
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A repository that answers `429 Too Many Requests` a few times does not fail the build (#1592).
 *
 * Gradle retries a transient repository error (a timeout, 408, 429 or 5xx) on its own, but by
 * default only three attempts, waiting 1 s and then 2 s. Maven Central's rate limiting outlasts that:
 * on 16–17 Nov five runs went red on a 429 before building anything. The repository's
 * `gradle.properties` raises the attempts and the first wait; this builds a project with exactly those
 * properties against a local repository that refuses its first [REFUSALS] requests.
 */
class RepositoryRetryTest {
    private val root = File("..")

    /** The `systemProp.org.gradle.internal.repository.*` lines of [file], the settings under test. */
    private fun retryLines(file: File): List<String> =
        file.readLines().map(String::trim).filter { it.startsWith(RETRY_PREFIX) }

    @Test
    fun aDependencyBehindAFewRateLimitedAnswersResolves() {
        val refused = AtomicInteger()
        val served = AtomicInteger()
        val requests = java.util.Collections.synchronizedList(mutableListOf<String>())
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val body = artifact(exchange.requestURI.path)
            requests += "${exchange.requestMethod} ${exchange.requestURI.path} refused=${refused.get()}"
            when {
                refused.get() < REFUSALS -> {
                    refused.incrementAndGet()
                    exchange.sendResponseHeaders(TOO_MANY_REQUESTS, -1)
                }
                body == null -> exchange.sendResponseHeaders(NOT_FOUND, -1)
                else -> {
                    served.incrementAndGet()
                    val bytes = if (exchange.requestMethod == "HEAD") ByteArray(0) else body
                    exchange.sendResponseHeaders(OK, if (exchange.requestMethod == "HEAD") -1 else bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                }
            }
            exchange.close()
        }
        server.start()
        val dir = createTempDirectory("repository-retry").toFile()
        try {
            val port = server.address.port
            File(dir, "settings.gradle.kts").writeText("rootProject.name = \"retry\"\n")
            File(dir, "gradle.properties").writeText(retryLines(File(root, "gradle.properties")).joinToString("\n") + "\n")
            File(dir, "build.gradle.kts").writeText(
                """
                plugins { java }
                repositories { maven { url = uri("http://127.0.0.1:$port/"); isAllowInsecureProtocol = true } }
                dependencies { implementation("org.example:lib:1.0") }
                tasks.register("resolveLib") { val files = configurations.compileClasspath; doLast { println("RESOLVED " + files.get().files.map { it.name }) } }
                """.trimIndent(),
            )
            // A build that fails, as with Gradle's three default attempts, throws here with its output.
            val result = runCatching {
                GradleRunner.create()
                    .withProjectDir(dir)
                    // A daemon of its own: one another build started reads the retry settings it started with.
                    .withTestKitDir(File(dir, "testkit"))
                    .withArguments("resolveLib", "--refresh-dependencies")
                    .build()
            }.getOrElse { throw AssertionError("requests: $requests", it) }
            assertTrue("RESOLVED [lib-1.0.jar]" in result.output, result.output)
            assertEquals(REFUSALS, refused.get(), "the repository refused its first requests")
        } finally {
            server.stop(0)
            dir.deleteRecursively()
        }
    }

    @Test
    fun buildLogicRetriesAsTheRootBuildDoes() {
        val rootLines = retryLines(File(root, "gradle.properties"))
        assertTrue(rootLines.isNotEmpty(), "the root build sets its retry attempts and first wait")
        assertEquals(rootLines, retryLines(File(root, "build-logic/gradle.properties")), "build-logic, run on its own, retries the same")
    }

    /** The POM or jar of `org.example:lib:1.0` at [path], or null for anything else. */
    private fun artifact(path: String): ByteArray? = when {
        path.endsWith("/org/example/lib/1.0/lib-1.0.pom") -> POM.toByteArray()
        path.endsWith("/org/example/lib/1.0/lib-1.0.jar") -> EMPTY_JAR
        else -> null
    }

    private companion object {
        const val RETRY_PREFIX = "systemProp.org.gradle.internal.repository."

        /** More than Gradle's default three attempts can absorb, fewer than the repository's settings allow. */
        const val REFUSALS = 3
        const val OK = 200
        const val NOT_FOUND = 404
        const val TOO_MANY_REQUESTS = 429
        val POM = """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>org.example</groupId><artifactId>lib</artifactId><version>1.0</version>
            </project>
        """.trimIndent()

        /** An empty zip: the end-of-central-directory record alone. */
        val EMPTY_JAR = byteArrayOf(0x50, 0x4b, 0x05, 0x06, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
    }
}

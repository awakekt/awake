/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build

import com.awakekt.awake.build.probe.PortalProbePlugin
import com.sun.net.httpserver.HttpServer
import org.gradle.testkit.runner.GradleRunner
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.io.path.createTempDirectory

/**
 * A local Maven repository for the plugin-resolution tests (#1592): it either serves the probe
 * plugin's marker, POM and jar, answering 404 for anything else, or answers every request with one
 * error [status], as a repository in an outage or rate-limiting does. It counts the requests it gets.
 */
internal class ProbePluginRepository private constructor(private val status: Int?) : AutoCloseable {
    val requests = AtomicInteger()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

    init {
        server.createContext("/") { exchange ->
            requests.incrementAndGet()
            val body = if (status == null) files[exchange.requestURI.path] else null
            when {
                status != null -> exchange.sendResponseHeaders(status, -1)
                body == null -> exchange.sendResponseHeaders(NOT_FOUND, -1)
                else -> {
                    val head = exchange.requestMethod == "HEAD"
                    exchange.sendResponseHeaders(OK, if (head) -1 else body.size.toLong())
                    if (!head) exchange.responseBody.use { it.write(body) }
                }
            }
            exchange.close()
        }
        server.start()
    }

    /** This repository as a `repositories {}` entry. */
    val declaration: String
        get() = "maven { url = uri(\"http://127.0.0.1:${server.address.port}/\"); isAllowInsecureProtocol = true }"

    override fun close() = server.stop(0)

    companion object {
        const val PLUGIN_ID = "portal.probe"
        const val VERSION = "1.0"
        const val MARKER_GROUP = PLUGIN_ID
        const val MARKER_MODULE = "$PLUGIN_ID.gradle.plugin"
        private const val OK = 200
        private const val NOT_FOUND = 404

        /** A repository that serves the probe plugin. */
        fun serving() = ProbePluginRepository(status = null)

        /** A repository that answers every request with [status]. */
        fun failing(status: Int) = ProbePluginRepository(status)

        private val files: Map<String, ByteArray> by lazy {
            mapOf(
                "/${PLUGIN_ID.replace('.', '/')}/$MARKER_MODULE/$VERSION/$MARKER_MODULE-$VERSION.pom" to
                    pom(MARKER_GROUP, MARKER_MODULE, dependency = true),
                "/com/example/probe/$VERSION/probe-$VERSION.pom" to pom("com.example", "probe", dependency = false),
                "/com/example/probe/$VERSION/probe-$VERSION.jar" to probeJar(),
            )
        }

        private fun pom(group: String, artifact: String, dependency: Boolean): ByteArray {
            val dependencies = if (dependency) {
                "<dependencies><dependency><groupId>com.example</groupId><artifactId>probe</artifactId><version>$VERSION</version></dependency></dependencies>"
            } else {
                ""
            }
            // A plugin marker is a POM with no jar of its own, as the Portal and Central publish them.
            val packaging = if (dependency) "<packaging>pom</packaging>" else ""
            return """<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>
                <groupId>$group</groupId><artifactId>$artifact</artifactId><version>$VERSION</version>$packaging$dependencies</project>""".toByteArray()
        }

        /** A jar holding [PortalProbePlugin] and the descriptor that maps [PLUGIN_ID] to it. */
        private fun probeJar(): ByteArray {
            val className = PortalProbePlugin::class.java.name
            val classFile = className.replace('.', '/') + ".class"
            val bytes = checkNotNull(PortalProbePlugin::class.java.classLoader.getResourceAsStream(classFile)).use { it.readBytes() }
            val out = ByteArrayOutputStream()
            JarOutputStream(out).use { jar ->
                jar.putNextEntry(JarEntry(classFile))
                jar.write(bytes)
                jar.closeEntry()
                jar.putNextEntry(JarEntry("META-INF/gradle-plugins/$PLUGIN_ID.properties"))
                jar.write("implementation-class=$className\n".toByteArray())
                jar.closeEntry()
            }
            return out.toByteArray()
        }
    }
}

/**
 * Runs [run] on a build in a fresh directory that applies the probe plugin, resolving it from
 * [repositories] (each a `repositories {}` entry), and deletes the directory afterwards: each TestKit
 * directory holds its own Gradle API jar, about 180 MB.
 */
internal fun <T> probeBuild(repositories: List<String>, run: (GradleRunner) -> T): T {
    val dir = createTempDirectory("probe-plugin-build").toFile()
    try {
        File(dir, "settings.gradle.kts").writeText(
            "pluginManagement { repositories {\n${repositories.joinToString("\n")}\n} }\nrootProject.name = \"probe\"\n",
        )
        File(dir, "build.gradle.kts").writeText(
            "plugins { id(\"${ProbePluginRepository.PLUGIN_ID}\") version \"${ProbePluginRepository.VERSION}\" }\n",
        )
        return run(
            GradleRunner.create().withProjectDir(dir).withTestKitDir(File(dir, "testkit"))
                .withArguments("probe", "--refresh-dependencies"),
        )
    } finally {
        dir.deleteRecursively()
    }
}

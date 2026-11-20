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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A plugin Maven Central serves still resolves while the Plugin Portal is down, once the Portal is
 * listed last (#1592), and does not when it is listed first, as it was.
 *
 * Offline: one local server stands for Central and serves a plugin's marker, POM and jar, and another
 * stands for the Portal and answers `503` to everything, as a Portal outage does.
 */
class PluginPortalOutageTest {
    @Test
    fun aCentralPluginResolvesDuringAPortalOutageWhenThePortalIsLast() {
        withRepositories { central, portal ->
            val result = build(central, portal, portalFirst = false).build()
            assertTrue("PROBE APPLIED" in result.output, result.output)
        }
    }

    @Test
    fun thePortalListedFirstFailsTheSameBuild() {
        withRepositories { central, portal ->
            val result = build(central, portal, portalFirst = true).buildAndFail()
            // The Portal's 503 stops the search before Central, which holds the plugin, is asked, and
            // Gradle reports it as a plugin found nowhere: the message CI printed for vanniktech's.
            assertTrue("could not resolve plugin artifact 'portal.probe:portal.probe.gradle.plugin:1.0'" in result.output, result.output)
            assertEquals(0, centralRequests.get(), "Central was asked although the Portal, listed before it, failed")
        }
    }

    private fun build(central: Int, portal: Int, portalFirst: Boolean): GradleRunner {
        val dir = createTempDirectory("plugin-portal-outage").toFile()
        val repos = listOf("maven { url = uri(\"http://127.0.0.1:$central/\"); isAllowInsecureProtocol = true }")
            .let { if (portalFirst) listOf(portalRepo(portal)) + it else it + portalRepo(portal) }
        File(dir, "settings.gradle.kts").writeText(
            "pluginManagement { repositories {\n${repos.joinToString("\n")}\n} }\nrootProject.name = \"outage\"\n",
        )
        File(dir, "build.gradle.kts").writeText("plugins { id(\"$PLUGIN_ID\") version \"$VERSION\" }\n")
        return GradleRunner.create().withProjectDir(dir).withTestKitDir(File(dir, "testkit")).withArguments("probe", "--refresh-dependencies")
    }

    private fun portalRepo(port: Int) = "maven { url = uri(\"http://127.0.0.1:$port/\"); isAllowInsecureProtocol = true }"

    /** Runs [block] with a local Central serving the probe plugin and a local Portal answering 503. */
    private val centralRequests = AtomicInteger()

    private fun withRepositories(block: (central: Int, portal: Int) -> Unit) {
        val files = mapOf(
            "/${PLUGIN_ID.replace('.', '/')}/$PLUGIN_ID.gradle.plugin/$VERSION/$PLUGIN_ID.gradle.plugin-$VERSION.pom" to
                pom(PLUGIN_ID, "$PLUGIN_ID.gradle.plugin", dependency = true),
            "/com/example/probe/$VERSION/probe-$VERSION.pom" to pom("com.example", "probe", dependency = false),
            "/com/example/probe/$VERSION/probe-$VERSION.jar" to probeJar(),
        )
        val central = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        central.createContext("/") { exchange ->
            centralRequests.incrementAndGet()
            val body = files[exchange.requestURI.path]
            if (body == null) {
                exchange.sendResponseHeaders(NOT_FOUND, -1)
            } else {
                val head = exchange.requestMethod == "HEAD"
                exchange.sendResponseHeaders(OK, if (head) -1 else body.size.toLong())
                if (!head) exchange.responseBody.use { it.write(body) }
            }
            exchange.close()
        }
        val portal = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        portal.createContext("/") { exchange ->
            exchange.sendResponseHeaders(UNAVAILABLE, -1)
            exchange.close()
        }
        central.start()
        portal.start()
        try {
            block(central.address.port, portal.address.port)
        } finally {
            central.stop(0)
            portal.stop(0)
        }
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

    private companion object {
        const val PLUGIN_ID = "portal.probe"
        const val VERSION = "1.0"
        const val OK = 200
        const val NOT_FOUND = 404
        const val UNAVAILABLE = 503
    }
}

/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks

import com.sun.net.httpserver.HttpServer
import org.gradle.api.GradleException
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CentralAvailabilityTest {
    @Test
    fun waitsForVariantMetadataAndBinaryAfterRootPomAppears() = repository { base, files ->
        val module = base.resolve("root/1.0/root-1.0.module")
        val variant = base.resolve("desktop/1.0/desktop-1.0.module")
        val binary = base.resolve("desktop/1.0/desktop-1.0.jar")
        files["/root/1.0/root-1.0.pom"] = "<project/>"
        val gate = CentralAvailability(base, listOf(module))
        assertEquals(listOf(module), gate.missingFiles())
        files[module.path] = """{"variants":[{"available-at":{"url":"../../desktop/1.0/desktop-1.0.module"}}]}"""
        assertTrue(variant in gate.missingFiles())
        files[variant.path] = """{"variants":[{"files":[{"url":"desktop-1.0.jar"}]}]}"""
        files["/desktop/1.0/desktop-1.0.pom"] = "<project/>"
        assertEquals(listOf(binary), gate.missingFiles())
        files[binary.path] = "binary"
        assertTrue(gate.missingFiles().isEmpty())
    }

    @Test
    fun rejectsArtifactLinksOutsideTheRepository() = repository { base, files ->
        val module = base.resolve("root.module")
        files[module.path] = """{"variants":[{"files":[{"url":"https://example.com/binary.jar"}]}]}"""
        assertFailsWith<IllegalArgumentException> { CentralAvailability(base, listOf(module)).missingFiles() }
    }

    @Test
    fun authenticationFailureIsNotTreatedAsPropagationDelay() = repository(status = 401) { base, _ ->
        assertFailsWith<GradleException> { CentralAvailability(base, listOf(base.resolve("root.module"))).missingFiles() }
    }

    @Test
    fun waitsForTheCoreModulesTheWebGpuPinDependsOn() = repository { base, files ->
        val local = createTempDirectory("maven-local")
        try {
            // A root that points at its wasm module, which holds the edges: two Core dependencies, its own
            // family, a third-party library and a constraint, of which only the Core dependencies count.
            val root = local.resolve("webgpu/1.2.3-SNAPSHOT/webgpu-1.2.3-SNAPSHOT.module")
            val wasm = local.resolve("webgpu-wasm-js/1.2.3-SNAPSHOT/webgpu-wasm-js-1.2.3-SNAPSHOT.module")
            Files.createDirectories(root.parent)
            Files.createDirectories(wasm.parent)
            Files.writeString(
                root,
                """{"variants":[{"name":"wasm","available-at":{"url":"../../webgpu-wasm-js/1.2.3-SNAPSHOT/webgpu-wasm-js-1.2.3-SNAPSHOT.module"}}]}""",
            )
            Files.writeString(
                wasm,
                """
                {"variants":[{"name":"wasmJsRuntimeElements","dependencies":[
                  {"group":"com.awakekt.awake.core","module":"math","version":{"requires":"1.2.3"}},
                  {"group":"com.awakekt.awake.engine","module":"platform","version":{"requires":"1.2.3"}},
                  {"group":"com.awakekt.awake.backend","module":"webgpu-wasm-js","version":{"requires":"1.2.3-SNAPSHOT"}},
                  {"group":"org.jetbrains.kotlin","module":"kotlin-stdlib","version":{"requires":"2.4.10"}}
                ],"dependencyConstraints":[
                  {"group":"com.awakekt.awake.scene","module":"runtime","version":{"strictly":"1.2.3"}}
                ]}]}
                """.trimIndent(),
            )

            val core = pinnedCoreModules(root, "1.2.3")
            assertEquals(listOf("com.awakekt.awake.core:math", "com.awakekt.awake.engine:platform"), core)

            val modules = core.map { centralModule(base, it, "1.2.3") }
            assertEquals(base.resolve("com/awakekt/awake/core/math/1.2.3/math-1.2.3.module"), modules.first())
            val gate = CentralAvailability(base, modules)
            assertEquals(modules.flatMap { listOf(it, URI(it.toString().removeSuffix(".module") + ".pom")) }, gate.missingFiles())
            for (module in modules) {
                files[module.path] = """{"variants":[{"name":"jvm"}]}"""
                files[module.path.removeSuffix(".module") + ".pom"] = "<project/>"
            }
            assertTrue(gate.missingFiles().isEmpty())
        } finally {
            local.toFile().deleteRecursively()
        }
    }

    private fun repository(status: Int? = null, test: (URI, MutableMap<String, String>) -> Unit) {
        val files = ConcurrentHashMap<String, String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val body = files[exchange.requestURI.path]?.toByteArray()
            when {
                status != null -> exchange.sendResponseHeaders(status, -1)
                body == null -> exchange.sendResponseHeaders(404, -1)
                exchange.requestMethod == "HEAD" -> exchange.sendResponseHeaders(200, -1)
                else -> {
                    exchange.sendResponseHeaders(200, body.size.toLong())
                    exchange.responseBody.write(body)
                }
            }
            exchange.close()
        }
        server.start()
        try {
            test(URI("http://127.0.0.1:${server.address.port}/"), files)
        } finally {
            server.stop(0)
        }
    }
}

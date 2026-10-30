/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.cli

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.Sha256
import com.awakekt.awake.project.BundleKeyProvider
import com.awakekt.awake.project.IndexedAssetSource
import com.awakekt.awake.project.ProjectIndex
import com.awakekt.awake.project.ProjectIndexEntry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** `awake bundle --key-id`: bundles sealed under a key read from a file, never from the command line. */
class ProjectBundleEncryptionTest {
    private val root: File = createTempDirectory("awake-bundle-key").toFile()
    private val output: File get() = root.resolve("build/published")
    private val key = ByteArray(32) { (it * 7 + 1).toByte() }

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun aKeyFileSealsTheBundlesAndTheIndexNamesOnlyItsId() {
        project()
        root.resolve("secrets/bundle.key").apply { parentFile.mkdirs() }.writeBytes(key)

        val run = awake("bundle", "build/project.index.json", "--output", "build/published", "--project", ".", "--key-id", "harbor-1", "--key-file", "secrets/bundle.key")

        assertEquals(0, run.code, run.all)
        val indexText = output.resolve("project.index.json").readText()
        val index = Json.decodeFromString(ProjectIndex.serializer(), indexText)
        assertEquals(ProjectIndex.ENCRYPTED_FORMAT_VERSION, index.formatVersion)
        assertEquals("harbor-1", index.encryption?.keyId)
        val source = IndexedAssetSource(index, { url -> output.resolve(url).readBytes() }, keys = BundleKeyProvider { key })
        FILES.forEach { (path, text) -> assertContentEquals(text.encodeToByteArray(), runBlocking { source.read(AssetPath(path)).getOrThrow() }, path) }
        output.resolve("bundles").listFiles()!!.forEach { bundle ->
            FILES.values.forEach { text -> assertFalse(bundle.readText(Charsets.ISO_8859_1).contains(text), "${bundle.name} holds plaintext") }
        }
    }

    @Test
    fun aKeyIdNeedsExactlyOneSourceAndAKeyFileNeedsAnId() {
        project()

        val neither = awake("bundle", "build/project.index.json", "--output", "build/published", "--project", ".", "--key-id", "harbor-1")
        val noId = awake("bundle", "build/project.index.json", "--output", "build/published", "--project", ".", "--key-file", "bundle.key")
        val missing = awake("bundle", "build/project.index.json", "--output", "build/published", "--project", ".", "--key-id", "k", "--key-file", "none.key")

        assertEquals(2, neither.code, neither.all)
        assertContains(neither.err, "exactly one of --key-file")
        assertEquals(2, noId.code, noId.all)
        assertContains(noId.err, "need --key-id")
        assertContains(missing.err, "doesn't exist")
    }

    private fun project() {
        FILES.forEach { (path, text) -> root.resolve(path).apply { parentFile.mkdirs() }.writeText(text) }
        val index = ProjectIndex(
            files = FILES.map { (path, text) ->
                val bytes = text.encodeToByteArray()
                ProjectIndexEntry(path = path, sizeBytes = bytes.size.toLong(), sha256 = Sha256.digestHex(bytes), url = "files/$path")
            },
        )
        root.resolve("build/project.index.json").apply { parentFile.mkdirs() }.writeText(Json.encodeToString(ProjectIndex.serializer(), index))
    }

    private fun awake(vararg args: String): Run {
        val out = StringBuilder()
        val err = StringBuilder()
        val code = AwakeCli(out, err, root).run(args.toList())
        return Run(code, out.toString(), err.toString())
    }

    private class Run(val code: Int, val out: String, val err: String) {
        val all: String get() = "exit $code\n$out\n$err"
    }

    private companion object {
        val FILES = linkedMapOf(
            "awake.project.json" to """{"formatVersion":1,"id":"com.example.harbor","name":"Harbor","version":"1.0.0"}""",
            "scenes/harbor.scene.json" to """{"version":1,"name":"harbor","nodes":[]}""",
            "assets/textures/wood.txt" to "grain of the old pier",
        )
    }
}

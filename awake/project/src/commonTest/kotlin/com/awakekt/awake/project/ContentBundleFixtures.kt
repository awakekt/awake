/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import com.awakekt.awake.core.io.Sha256
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/** A small published project, its loose index, and the bundles [ContentBundleWriter] makes of it. */
internal class BundledProject(
    val files: Map<String, ByteArray> = SAMPLE_FILES,
    writer: ContentBundleWriter = ContentBundleWriter(),
) {
    /** The loose index, each file at `files/<path>`. */
    val looseIndex = looseIndexOf(files)

    /** Every bundle written, by URL. */
    val bundles = LinkedHashMap<String, WrittenContentBundle>()

    /** The bundled index. */
    val index: ProjectIndex = runSuspend {
        writer.write(looseIndex, { entry -> files.getValue(entry.path) }) { bundle -> bundles[bundle.url] = bundle }
    }

    /** Serves the loose files and the bundles by URL, as a static host would. */
    fun server(override: Map<String, ByteArray> = emptyMap()): suspend (String) -> ByteArray {
        val byUrl = looseIndex.files.associate { it.url to files.getValue(it.path) } + bundles.mapValues { it.value.bytes } + override
        return { url -> byUrl[url] ?: error("404 $url") }
    }

    companion object {
        val SAMPLE_FILES: Map<String, ByteArray> = linkedMapOf(
            "awake.project.json" to "{\"formatVersion\":1,\"id\":\"com.example.harbor-town\"}".encodeToByteArray(),
            "scenes/main.scene.json" to "{\"version\": 1, \"name\": \"harbor\"}".encodeToByteArray(),
            "scenes/pier.scene.json" to "{\"version\": 1, \"name\": \"pier\"}".encodeToByteArray(),
            "assets/models/crate.glb" to ByteArray(300) { (it * 7).toByte() },
            "assets/models/buoy.glb" to ByteArray(200) { (it * 3).toByte() },
            "assets/textures/wood.png" to ByteArray(120) { (it + 1).toByte() },
            "assets/textures/wood-copy.png" to ByteArray(120) { (it + 1).toByte() },
            "assets/sounds/bell.ogg" to ByteArray(80) { 9 },
        )
    }
}

/** A loose index of [files], each at `files/<path>`. */
internal fun looseIndexOf(files: Map<String, ByteArray>): ProjectIndex = ProjectIndex(
    files = files.map { (path, bytes) ->
        ProjectIndexEntry(path = path, sizeBytes = bytes.size.toLong(), sha256 = Sha256.digestHex(bytes), url = "files/$path")
    },
)

/** A [ContentCache] in memory. */
internal class MemoryContentCache : ContentCache {
    val stored = mutableMapOf<String, ByteArray>()

    override suspend fun get(sha256: String): ByteArray? = stored[sha256]

    override suspend fun put(sha256: String, bytes: ByteArray) {
        stored[sha256] = bytes
    }
}

/** Runs [block], which suspends on nothing, to completion here. */
internal fun <T> runSuspend(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
    return checkNotNull(outcome) { "the block suspended" }.getOrThrow()
}

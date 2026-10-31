/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.meshoptimizer

import com.awakekt.awake.asset.gltf.GltfParser
import com.awakekt.awake.asset.meshlod.GltfLodCooker
import com.awakekt.awake.asset.meshlod.LodCookOutcome
import com.awakekt.awake.asset.meshlod.ProjectLodSettings
import java.io.File
import java.net.URLDecoder

/**
 * Cooks levels of detail into a project's glTF models, with the project's `awake.lod.json` settings
 * (defaults when it has none) and each asset's own override or opt-out.
 */
object LodCook {
    /**
     * Cooks each of [assets], paths relative to [project], into the same relative path under [output].
     * A `.gltf` document's external buffers and images are copied alongside it. Returns one line per asset.
     */
    fun run(project: File, assets: List<String>, output: File): List<String> {
        val settingsFile = project.resolve(ProjectLodSettings.FILE_NAME)
        val settings = if (settingsFile.isFile) ProjectLodSettings.decode(settingsFile.readText()) else ProjectLodSettings()
        return assets.map { asset ->
            val source = project.resolve(asset)
            require(source.isFile) { "no asset $asset in ${project.path}" }
            val bytes = source.readBytes()
            val glb = bytes.size >= GLB_MAGIC.size && bytes.copyOf(GLB_MAGIC.size).contentEquals(GLB_MAGIC)
            val external = if (glb) emptyMap() else sidecars(source, bytes)
            val result = GltfLodCooker.cook(bytes, settings.forAsset(asset), external)
            val target = output.resolve(asset).also { it.parentFile.mkdirs() }
            target.writeBytes(result.bytes)
            external.keys.forEach { uri -> source.parentFile.resolve(decoded(uri)).copyTo(target.parentFile.resolve(decoded(uri)), true) }
            describe(asset, result.outcome, result.meshes.map { it.triangles })
        }
    }

    private fun sidecars(source: File, bytes: ByteArray): Map<String, ByteArray> =
        GltfParser.externalUris(bytes.decodeToString()).associateWith { source.parentFile.resolve(decoded(it)).readBytes() }

    private fun decoded(uri: String): String = URLDecoder.decode(uri.replace("+", "%2B"), Charsets.UTF_8)

    private fun describe(asset: String, outcome: LodCookOutcome, triangles: List<List<Int>>): String = when (outcome) {
        LodCookOutcome.Generated -> "$asset: " + triangles.joinToString("; ") { levels -> levels.joinToString(" -> ") } + " triangles"
        LodCookOutcome.OptedOut -> "$asset: opted out, copied unchanged"
        LodCookOutcome.AuthoredLods -> "$asset: already carries levels of detail, kept as authored"
        LodCookOutcome.NothingToSimplify -> "$asset: nothing to simplify, copied unchanged"
    }

    private val GLB_MAGIC = "glTF".encodeToByteArray()
}

/** The `cookLods` task's entry point: `<project> <asset> [<asset> ...] --output <dir>`. */
object LodCookMain {
    /** Cooks the assets [args] names; see [LodCook.run]. */
    @JvmStatic
    fun main(args: Array<String>) {
        val outputFlag = args.indexOf("--output")
        require(outputFlag >= 0 && outputFlag + 1 < args.size && args.size >= 4) {
            "Usage: <project> <asset> [<asset> ...] --output <dir>"
        }
        val positional = args.filterIndexed { i, _ -> i != outputFlag && i != outputFlag + 1 }
        LodCook.run(File(positional[0]), positional.drop(1), File(args[outputFlag + 1])).forEach(::println)
    }
}

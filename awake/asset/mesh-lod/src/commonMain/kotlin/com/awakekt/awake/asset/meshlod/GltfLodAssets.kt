/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.meshlod

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * The levels of detail of a glTF asset as whole assets, for a loader that draws a model as one
 * mesh: level `k` is the asset with each LOD chain's base node drawing its `k`-th level instead (or
 * its coarsest, for a chain with fewer levels). Everything else, and every node without a chain,
 * is unchanged, so a loader reads a level exactly as it reads the asset.
 *
 * @property count How many levels the asset has, the full one included: its longest chain's.
 * @property screenCoverage The smallest screen coverage each level draws at, from the first chain
 *   that gives `MSFT_screencoverage` with a value per level; null when none does.
 */
class GltfLodAssets private constructor(
    private val root: JsonObject,
    private val glb: GlbFile?,
    private val chains: List<GltfLodChain>,
    val count: Int,
    val screenCoverage: List<Float>?,
) {
    /** The asset at [level], `0` the full one, in the container it came in. */
    fun level(level: Int): ByteArray {
        require(level in 0 until count) { "level is $level; the asset has $count" }
        val nodes = (root["nodes"] as JsonArray).toMutableList()
        for (chain in chains) {
            val source = nodes[chain.levels[minOf(level, chain.levels.lastIndex)]].jsonObject
            var base = nodes[chain.node].jsonObject
            source["mesh"]?.let { base = base.with("mesh", it) }
            source["skin"]?.let { base = base.with("skin", it) }
            nodes[chain.node] = base
        }
        val json = Json.encodeToString(JsonObject.serializer(), root.with("nodes", JsonArray(nodes)))
        return glb?.let { GlbFile(json, it.bin).encode() } ?: json.encodeToByteArray()
    }

    companion object {
        /** [bytes]' levels, a GLB or a `.gltf` document; null when it carries none. */
        fun read(bytes: ByteArray): GltfLodAssets? {
            val glb = if (GlbFile.isGlb(bytes)) GlbFile.decode(bytes) else null
            val root = Json.parseToJsonElement(glb?.json ?: bytes.decodeToString()).jsonObject
            val chains = GltfLods.read(root)
            if (chains.isEmpty()) return null
            val count = chains.maxOf { it.levels.size }
            val coverage = chains.firstNotNullOfOrNull { chain -> chain.screenCoverage?.takeIf { it.size >= count }?.take(count) }
            return GltfLodAssets(root, glb, chains, count, coverage)
        }
    }
}

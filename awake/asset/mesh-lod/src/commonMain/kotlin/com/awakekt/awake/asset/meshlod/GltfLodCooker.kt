/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.meshlod

import com.awakekt.awake.asset.gltf.GltfMesh
import com.awakekt.awake.asset.gltf.GltfParser
import com.awakekt.awake.asset.gltf.parseMeshPrimitives
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * One mesh's generated levels.
 *
 * @property mesh The mesh's index in the source document.
 * @property name The mesh's name, if it has one.
 * @property triangles Triangles per level, the full mesh first.
 * @property errors Each generated level's simplification error, relative to the mesh's largest extent.
 */
data class MeshLodReport(val mesh: Int, val name: String?, val triangles: List<Int>, val errors: List<Float>)

/** What a cook did. */
enum class LodCookOutcome {
    /** Levels were generated for at least one mesh. */
    Generated,

    /** The asset's settings opt it out; it is unchanged. */
    OptedOut,

    /** The asset already carries levels of detail, which are kept as authored; it is unchanged. */
    AuthoredLods,

    /** No mesh could be simplified usefully, or no levels are configured; the asset is unchanged. */
    NothingToSimplify,
}

/** A cooked asset, in the container it came in, and what was done to it. */
class LodCookResult(val bytes: ByteArray, val outcome: LodCookOutcome, val meshes: List<MeshLodReport>)

/**
 * Generates levels of detail for every mesh of a glTF asset, at cook or import time, and stores them as
 * `MSFT_lod`: each node with a mesh gets one extra node per level, listed in its `MSFT_lod.ids`, with
 * the per-level screen coverage in its `MSFT_screencoverage` extras. A LOD node carries only its mesh
 * (and skin), and is drawn at its base node's transform.
 *
 * A level is an index buffer over the full mesh's own vertex accessors, so its vertices keep every
 * attribute exactly, skins and morph targets included, and only index data is added to the file.
 * Points and lines are left alone, and so is a level that would not cut triangles usefully.
 */
@Suppress("ReturnCount", "LoopWithTooManyJumpStatements") // Early outs for assets left unchanged.
object GltfLodCooker {
    private const val USEFUL_REDUCTION = 0.85f
    private const val TRIANGLES = 4

    /**
     * Cooks [bytes], a GLB or a `.gltf` document, with [settings].
     *
     * @param bytes A GLB file, or a `.gltf` document's UTF-8 JSON.
     * @param settings The asset's settings; see [ProjectLodSettings.forAsset].
     * @param externalResources The bytes of every external buffer or image the document references.
     * @throws com.awakekt.awake.asset.gltf.GltfFormatException If the asset is malformed.
     */
    fun cook(
        bytes: ByteArray,
        settings: MeshLodSettings = MeshLodSettings(),
        externalResources: Map<String, ByteArray> = emptyMap(),
    ): LodCookResult {
        if (!settings.enabled) return LodCookResult(bytes, LodCookOutcome.OptedOut, emptyList())
        val glb = if (GlbFile.isGlb(bytes)) GlbFile.decode(bytes) else null
        val root = Json.parseToJsonElement(glb?.json ?: bytes.decodeToString()).jsonObject
        if (GltfLods.hasAuthoredLods(root)) return LodCookResult(bytes, LodCookOutcome.AuthoredLods, emptyList())
        val primitives = GltfParser.parseMeshPrimitives(bytes, externalResources)
        val document = LodDocument(root, glb)
        val lodMeshes = HashMap<Int, Pair<List<Int>, MeshLodSettings>>()
        val reports = ArrayList<MeshLodReport>()
        val sourceNodes = document.nodes.size
        for (nodeIndex in 0 until sourceNodes) {
            val node = document.nodes[nodeIndex] as? JsonObject ?: continue
            val mesh = node["mesh"]?.jsonPrimitive?.intOrNull ?: continue
            val (levels, meshSettings) = lodMeshes.getOrPut(mesh) {
                val meshSettings = settings.forMesh((document.meshes.getOrNull(mesh) as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull)
                val names = JointImportance.jointNames(root, document.nodes, mesh)
                val generated = if (meshSettings.enabled) generate(document, mesh, primitives.getOrNull(mesh).orEmpty(), meshSettings, names) else null
                generated?.second?.let(reports::add)
                generated?.first.orEmpty() to meshSettings
            }
            if (levels.isEmpty()) continue
            val ids = levels.mapIndexed { level, lodMesh -> document.addNode(lodNode(node, lodMesh, level + 1)) }
            document.nodes[nodeIndex] = withLods(node, ids, coverage(meshSettings, ids.size))
        }
        if (reports.isEmpty()) return LodCookResult(bytes, LodCookOutcome.NothingToSimplify, emptyList())
        return LodCookResult(document.encode(), LodCookOutcome.Generated, reports.sortedBy { it.mesh })
    }

    /** The LOD meshes for [mesh], coarser each, and their report; null when none would help. */
    private fun generate(
        document: LodDocument,
        mesh: Int,
        decoded: List<GltfMesh>,
        settings: MeshLodSettings,
        jointNames: List<String?>,
    ): Pair<List<Int>, MeshLodReport>? {
        val meshJson = document.meshes.getOrNull(mesh) as? JsonObject ?: return null
        val primitives = meshJson["primitives"]?.jsonArray?.map { it.jsonObject } ?: return null
        if (primitives.size != decoded.size || primitives.any { (it["mode"]?.jsonPrimitive?.intOrNull ?: TRIANGLES) != TRIANGLES }) return null
        val name = meshJson["name"]?.jsonPrimitive?.contentOrNull
        val triangles = mutableListOf(decoded.sumOf { it.indices.size / 3 })
        val errors = ArrayList<Float>()
        val levels = ArrayList<Int>()
        val importance = decoded.map { JointImportance.perVertex(it, jointNames, settings.jointImportance) }
        for (level in settings.levels) {
            val simplified = decoded.mapIndexed { i, primitive -> simplify(primitive, level.ratio, settings, importance[i]) }
            val count = simplified.sumOf { it.triangleCount }
            if (count == 0 || count > triangles.last() * USEFUL_REDUCTION) break
            val lodPrimitives = primitives.mapIndexed { i, primitive ->
                primitive.with("indices", JsonPrimitive(document.addIndices(simplified[i].indices, decoded[i].positions.size / 3)))
            }
            val lodName = JsonPrimitive("${name ?: "mesh$mesh"}_LOD${levels.size + 1}")
            levels += document.addMesh(meshJson.with("primitives", JsonArray(lodPrimitives)).with("name", lodName))
            triangles += count
            errors += simplified.maxOf { it.error }
        }
        return if (levels.isEmpty()) null else levels to MeshLodReport(mesh, name, triangles, errors)
    }

    private fun simplify(mesh: GltfMesh, ratio: Float, settings: MeshLodSettings, importance: FloatArray?): SimplifiedMesh {
        val attributes = listOfNotNull(
            mesh.normals?.let { MeshAttribute(it, 3, settings.normalWeight) },
            mesh.uvs?.let { MeshAttribute(it, 2, settings.uvWeight) },
            mesh.colors?.let { MeshAttribute(it, 3, settings.colorWeight) },
        )
        val joints = mesh.jointIndices
        val weights = mesh.jointWeights
        val skin = if (joints != null && weights != null) SkinInfluences(joints, weights, weight = settings.skinWeight) else null
        val options = SimplifyOptions(targetRatio = ratio, lockBorders = settings.lockBorders)
        return AttributeMeshSimplifier.simplify(mesh.positions, mesh.indices, options, attributes, skin, importance)
    }

    /** A node for one level: its mesh and skin only, drawn at the base node's transform. */
    private fun lodNode(base: JsonObject, mesh: Int, level: Int): JsonObject {
        val name = base["name"]?.jsonPrimitive?.contentOrNull ?: "node"
        val fields = linkedMapOf("name" to JsonPrimitive("${name}_LOD$level"), "mesh" to JsonPrimitive(mesh))
        base["skin"]?.let { fields["skin"] = it.jsonPrimitive }
        return JsonObject(fields)
    }

    /** The base level's coverage, each kept level's, and the last configured level's for the coarsest kept one. */
    private fun coverage(settings: MeshLodSettings, levels: Int): List<Float> =
        listOf(settings.baseScreenCoverage) + settings.levels.take(levels - 1).map { it.screenCoverage } + settings.levels.last().screenCoverage

    private fun withLods(node: JsonObject, ids: List<Int>, coverage: List<Float>): JsonObject {
        val extensions = (node["extensions"] as? JsonObject ?: JsonObject(emptyMap()))
            .with(GltfLods.EXTENSION, JsonObject(mapOf("ids" to JsonArray(ids.map(::JsonPrimitive)))))
        val extras = (node["extras"] as? JsonObject ?: JsonObject(emptyMap()))
            .with(GltfLods.SCREEN_COVERAGE, JsonArray(coverage.map(::JsonPrimitive)))
        return node.with("extensions", extensions).with("extras", extras)
    }
}

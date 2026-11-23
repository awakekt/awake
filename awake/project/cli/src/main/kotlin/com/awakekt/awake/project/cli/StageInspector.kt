/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.cli

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.project.runtime.registerProjectComponents
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.core.transform.TransformSystem
import com.awakekt.awake.scene.director.SceneCameraDirector
import com.awakekt.awake.scene.director.SceneCameraRail
import com.awakekt.awake.scene.director.SceneStageMarker
import com.awakekt.awake.scene.director.SceneVirtualCamera
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.rendering.camera.SceneCamera
import com.awakekt.awake.scene.rendering.light.SceneDayCycle
import com.awakekt.awake.scene.rendering.light.SceneLight
import com.awakekt.awake.scene.rendering.mesh.SceneMeshRenderer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/**
 * Spatial summary of one named node on stage, giving humans and AI directors accurate world-space
 * coordinates, facing direction, and authored capabilities without launching a GPU.
 */
@Serializable
internal data class StageEntitySummary(
    val name: String,
    val path: String,
    val position: List<Float>,
    val scale: List<Float>,
    val forward: List<Float>,
    val components: List<String>,
    val mesh: String? = null,
    val lightType: String? = null,
    val lightIntensity: Float? = null,
    val cameraPrimary: Boolean? = null,
    val cameraFovYDegrees: Float? = null,
    val dayCycleTime: Float? = null,
    val railWaypoints: Int? = null,
    val markerTags: List<String> = emptyList(),
    val markerRadius: Float? = null,
)

/**
 * Complete spatial stage report for one scene and its project's director-relevant assets.
 */
@Serializable
internal data class StageReport(
    val scene: String,
    val sceneName: String?,
    val entityCount: Int,
    val entities: List<StageEntitySummary>,
    val cameras: List<String>,
    val rails: List<String>,
    val markers: List<String>,
    val lights: List<String>,
    val audioAssets: List<String>,
    val directorScripts: List<String>,
    val inputTakes: List<String>,
)

/**
 * `awake stage <scene>` and MCP `inspect_stage`: inspects a scene's world-space hierarchy, lights,
 * cameras, rails, stage markers, and project audio/script assets on CPU without needing a GPU.
 */
internal class StageInspector(private val project: ProjectScenes) {

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun inspect(sceneFile: File): StageReport {
        val doc = project.read(sceneFile)
        val registry = SceneComponentRegistry.scoped().registerProjectComponents()
        val world = World()
        doc.instantiate(world, registry)
        TransformSystem().update(world, 0f)

        val worldByName = LinkedHashMap<String, Transform>()
        world.queryEach(Name::class, Transform::class) { _, name, transform ->
            if (name.value.isNotBlank() && name.value !in worldByName) {
                worldByName[name.value] = transform
            }
        }

        val entities = mutableListOf<StageEntitySummary>()
        val cameras = mutableListOf<String>()
        val rails = mutableListOf<String>()
        val markers = mutableListOf<String>()
        val lights = mutableListOf<String>()

        fun visit(node: SceneNode, parentPath: String, index: Int) {
            val seg = node.name?.takeIf(String::isNotBlank) ?: "#${index + 1}"
            val fullPath = if (parentPath.isEmpty()) seg else "$parentPath/$seg"
            val liveTransform = node.name?.let(worldByName::get)
            val pos = if (liveTransform != null) {
                val m = liveTransform.worldMatrix
                listOf(round3(m.m03), round3(m.m13), round3(m.m23))
            } else {
                val p = node.transform.position
                listOf(round3(p.x), round3(p.y), round3(p.z))
            }
            val scale = node.transform.scale.let { listOf(round3(it.x), round3(it.y), round3(it.z)) }
            val fwd = if (liveTransform != null && liveTransform.worldVersion > 0) {
                val m = liveTransform.worldMatrix
                val v = Vec3f(-m.m02, -m.m12, -m.m22).normalize()
                listOf(round3(v.x), round3(v.y), round3(v.z))
            } else {
                val yaw = node.transform.rotation.y
                listOf(round3(sin(yaw)), 0f, round3(-cos(yaw)))
            }

            val compNames = node.components.map { componentLabel(it) }
            val mesh = node.components.filterIsInstance<SceneMeshRenderer>().firstOrNull()?.mesh
            val light = node.components.filterIsInstance<SceneLight>().firstOrNull()
            val cam = node.components.filterIsInstance<SceneCamera>().firstOrNull()
            val vcam = node.components.filterIsInstance<SceneVirtualCamera>().firstOrNull()
            val day = node.components.filterIsInstance<SceneDayCycle>().firstOrNull()
            val rail = node.components.filterIsInstance<SceneCameraRail>().firstOrNull()
            val marker = node.components.filterIsInstance<SceneStageMarker>().firstOrNull()

            val displayName = node.name?.takeIf(String::isNotBlank) ?: fullPath
            if (cam != null || vcam != null) cameras += displayName
            if (rail != null) rails += displayName
            if (marker != null) markers += displayName
            if (light != null) lights += displayName

            entities += StageEntitySummary(
                name = displayName,
                path = fullPath,
                position = pos,
                scale = scale,
                forward = fwd,
                components = compNames,
                mesh = mesh,
                lightType = light?.type?.name?.lowercase(),
                lightIntensity = light?.intensity,
                cameraPrimary = cam?.primary,
                cameraFovYDegrees = cam?.fovYDegrees ?: vcam?.lens?.fovYDegrees,
                dayCycleTime = day?.time,
                railWaypoints = rail?.waypoints?.size,
                markerTags = marker?.tags.orEmpty(),
                markerRadius = marker?.radius,
            )
            node.children.forEachIndexed { childIndex, child -> visit(child, fullPath, childIndex) }
        }

        doc.nodes.forEachIndexed { idx, node -> visit(node, "", idx) }

        val audioAssets = findAssets(".wav", ".ogg")
        val directorScripts = findAssets(".director.json", ".reel.json")
        val inputTakes = findAssets(".take.json")

        return StageReport(
            scene = project.relative(sceneFile),
            sceneName = doc.name,
            entityCount = entities.size,
            entities = entities,
            cameras = cameras,
            rails = rails,
            markers = markers,
            lights = lights,
            audioAssets = audioAssets,
            directorScripts = directorScripts,
            inputTakes = inputTakes,
        )
    }

    fun formatHuman(report: StageReport): String = buildString {
        appendLine("stage: ${report.scene} (${report.sceneName ?: "unnamed"}), ${report.entityCount} nodes")
        if (report.cameras.isNotEmpty()) appendLine("  cameras: ${report.cameras.joinToString()}")
        if (report.lights.isNotEmpty()) appendLine("  lights: ${report.lights.joinToString()}")
        if (report.rails.isNotEmpty()) appendLine("  rails: ${report.rails.joinToString()}")
        if (report.markers.isNotEmpty()) appendLine("  markers: ${report.markers.joinToString()}")
        if (report.audioAssets.isNotEmpty()) appendLine("  audio: ${report.audioAssets.joinToString()}")
        if (report.directorScripts.isNotEmpty()) appendLine("  scripts: ${report.directorScripts.joinToString()}")
        if (report.inputTakes.isNotEmpty()) appendLine("  takes: ${report.inputTakes.joinToString()}")
        appendLine("  entities:")
        report.entities.forEach { entity ->
            val comps = if (entity.components.isEmpty()) "" else " [${entity.components.joinToString()}]"
            appendLine("    ${entity.path}  pos=${entity.position}  fwd=${entity.forward}$comps")
        }
    }

    fun toJson(report: StageReport): String = PRETTY_JSON.encodeToString(StageReport.serializer(), report)

    private fun findAssets(vararg suffixes: String): List<String> = project.root.walkTopDown()
        .onEnter { dir -> dir == project.root || dir.name !in SKIPPED_DIRS && !dir.name.startsWith(".") }
        .filter { file -> file.isFile && suffixes.any { file.name.endsWith(it, ignoreCase = true) } }
        .map(project::relative)
        .sorted()
        .toList()

    private fun componentLabel(component: SceneComponent): String = when (component) {
        is SceneCameraDirector -> "camera_director"
        is SceneVirtualCamera -> "virtual_camera"
        is SceneCameraRail -> "camera_rail"
        is SceneStageMarker -> "stage_marker"
        else -> component::class.simpleName?.removePrefix("Scene")?.lowercase() ?: "component"
    }

    private fun round3(value: Float): Float = ((value * THOUSAND).toInt()) / THOUSAND

    private companion object {
        val SKIPPED_DIRS = setOf("build", "node_modules", ".git", ".gradle")
        const val THOUSAND = 1000f
    }
}

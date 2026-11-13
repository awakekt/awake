/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.rendering.Camera
import com.awakekt.awake.scene.rendering.animation.AnimationSystem
import com.awakekt.awake.scene.rendering.animation.Animator
import com.awakekt.awake.scene.rendering.animation.LocomotionAnimation
import com.awakekt.awake.scene.rendering.animation.LocomotionAnimationSystem
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Verifies [SceneSystemPlan.preAnimation] and [SceneSystemSet.preAnimation] end to end through
 * [runProject], [sceneSystemsFor], and [checkProjectOpens].
 */
class ScenePreAnimationPlanTest {
    @Test
    fun preAnimationPoseUpdateDrivesTransformsLocomotionChildCameraAndClipInSameFrame() = runTest {
        val probe = PoseProbe()
        val project = loadProject(projectFiles(CHILD_TARGET_SCENE), capabilities = listOf(probe))
        val game = app { scene("play") { runProject(project) } }
        game.ready(TestRenderer())
        val world = game.requireService<SceneAppLifecycleRuntime>().world
        val actor = world.entityNamed("Actor")
        val mount = world.entityNamed("Mount")

        // Settle the initial observation at the origin before moving the actor on a single frame.
        game.update(DELTA, WIDTH, HEIGHT)
        probe.observations.clear()
        val actorVersionBefore = assertNotNull(world.get<Transform>(actor)).worldVersion
        val mountVersionBefore = assertNotNull(world.get<Transform>(mount)).worldVersion
        probe.nextPosition = Vec3f(0.1f, 0f, -0.05f)

        game.update(DELTA, WIDTH, HEIGHT)

        val observed = probe.observations.single()
        assertEquals(Vec3f(0.1f, 0f, -0.05f), observed.actorWorld)
        assertEquals(Vec3f(0.1f, 1.6f, -0.05f), observed.mountWorld)
        assertEquals(Vec3f(0.1f, 1.6f, -0.05f), observed.cameraCenter)
        assertEquals("run", observed.locomotionClip)
        assertEquals("run", observed.activeClip)
        assertTrue(observed.playbackSpeed > 0f, "speed-matched playback observed ground motion: ${observed.playbackSpeed}")
        assertTrue(observed.clipTime > 0f, "AnimationSystem advanced the selected clip in the same frame: ${observed.clipTime}")
        assertEquals(actorVersionBefore + 1, assertNotNull(world.get<Transform>(actor)).worldVersion)
        assertEquals(mountVersionBefore + 1, assertNotNull(world.get<Transform>(mount)).worldVersion)
        project.close()
    }

    @Test
    fun preAnimationPoseUpdateAlsoDrivesDirectRootCameraTargetInSameFrame() = runTest {
        val probe = PoseProbe()
        val project = loadProject(projectFiles(DIRECT_TARGET_SCENE), capabilities = listOf(probe))
        val game = app { scene("play") { runProject(project) } }
        game.ready(TestRenderer())

        game.update(DELTA, WIDTH, HEIGHT)
        probe.observations.clear()
        probe.nextPosition = Vec3f(0.12f, 0f, 0.04f)

        game.update(DELTA, WIDTH, HEIGHT)

        val observed = probe.observations.single()
        assertEquals(Vec3f(0.12f, 0f, 0.04f), observed.actorWorld)
        assertEquals(Vec3f(0.12f, 0f, 0.04f), observed.cameraCenter)
        assertEquals("run", observed.locomotionClip)
        assertTrue(observed.clipTime > 0f)
        project.close()
    }

    @Test
    fun sceneSystemsForPopulatesPreAnimationInCapabilityOrderLeavesFrameIntactAndClosesNewestFirst() {
        val closed = mutableListOf<String>()
        val first = TrackingCapability("com.example.harbor-town.first", closed, preName = "pose-a", frameName = "frame-a")
        val second = TrackingCapability("com.example.harbor-town.second", closed, preName = "pose-b")
        val frameOnly = TrackingCapability("com.example.harbor-town.frame-only", closed, frameName = "frame-only")
        val scene = SceneLoader.decode(CHILD_TARGET_SCENE)

        val withoutPre = sceneSystemsFor(scene, headlessServices(), listOf(frameOnly))
        assertEquals(emptyList(), withoutPre.preAnimation)
        withoutPre.close()
        closed.clear()

        val systems = sceneSystemsFor(scene, headlessServices(), listOf(first, second))
        assertEquals(listOf("pose-a", "pose-b"), systems.preAnimation.map { (it as NamedCloseableSystem).label })
        assertIs<LocomotionAnimationSystem>(systems.frame[systems.frame.size - 3])
        assertIs<AnimationSystem>(systems.frame[systems.frame.size - 2])
        assertEquals("frame-a", (systems.frame.last() as NamedCloseableSystem).label)

        systems.close()
        assertEquals(listOf("pose-b", "frame-a", "pose-a"), closed)
    }

    @Test
    fun duplicateSystemNamesAcrossPreAnimationAndOtherPhasesAreRefused() {
        val scene = SceneLoader.decode(CHILD_TARGET_SCENE)
        val clashesWithCore = capability("com.example.harbor-town.core-clash") { plan ->
            plan.preAnimation("animation") { system { _, _ -> } }
        }
        val clashesWithFrame = capability("com.example.harbor-town.phase-clash") { plan ->
            plan.preAnimation("shared") { system { _, _ -> } }
            plan.frame("shared") { system { _, _ -> } }
        }
        val clashesWithFixed = capability("com.example.harbor-town.fixed-clash") { plan ->
            plan.fixed("shared-step") { system { _, _ -> } }
            plan.preAnimation("shared-step") { system { _, _ -> } }
        }

        for (invalid in listOf(clashesWithCore, clashesWithFrame, clashesWithFixed)) {
            assertFailsWith<IllegalArgumentException> { sceneSystemsFor(scene, headlessServices(), listOf(invalid)) }
        }
    }

    @Test
    fun checkProjectOpensStepsPreAnimationSystemsBeforeFrameSystems() = runTest {
        val events = mutableListOf<String>()
        val ordered = capability("com.example.harbor-town.open-order") { plan ->
            plan.fixed("step") { system { _, _ -> events += "fixed" } }
            plan.preAnimation("pose") { system { _, _ -> events += "pre-animation" } }
            plan.frame("observe") { system { _, _ -> events += "frame" } }
        }
        val files = projectFiles(SIMPLE_SCENE)

        val issues = checkProjectOpens(files, scenes = listOf("scenes/main.scene.json"), capabilities = listOf(ordered))
        assertEquals(emptyList(), issues)
        assertEquals(listOf("fixed", "pre-animation", "frame"), events)

        val broken = capability("com.example.harbor-town.broken-pre") { plan ->
            plan.preAnimation("broken") { system { _, _ -> error("bad pre-animation step") } }
        }
        val failure = checkProjectOpens(files, scenes = listOf("scenes/main.scene.json"), capabilities = listOf(broken)).single()
        assertEquals(ProjectOpenIssueKind.SceneSystems, failure.kind)
    }

    private data class FrameObservation(
        val actorWorld: Vec3f,
        val mountWorld: Vec3f,
        val cameraCenter: Vec3f,
        val locomotionClip: String?,
        val activeClip: String?,
        val playbackSpeed: Float,
        val clipTime: Float,
    )

    private inner class PoseProbe : SceneFeature {
        override val id = "com.example.harbor-town.pose-probe"
        var nextPosition: Vec3f? = null
        val observations = mutableListOf<FrameObservation>()

        override fun plan(scene: SceneDocument, plan: SceneSystemPlan) {
            plan.preAnimation("remote-pose") {
                system { world, delta ->
                    val target = nextPosition
                    if (delta > 0f && target != null) {
                        val actor = world.entityNamed("Actor")
                        assertNotNull(world.get<Transform>(actor)).position.set(target)
                    }
                }
            }
            plan.frame("observer") {
                system { world, delta ->
                    if (delta > 0f) observations += observeFrame(world)
                }
            }
        }

        private fun observeFrame(world: World): FrameObservation {
            val actor = world.entityNamed("Actor")
            val mount = world.findEntityNamed("Mount")
            val camera = world.entityNamed("Camera")
            val actorMatrix = assertNotNull(world.get<Transform>(actor)).worldMatrix
            val mountMatrix = mount?.let { assertNotNull(world.get<Transform>(it)).worldMatrix } ?: actorMatrix
            val animator = assertNotNull(world.get<Animator>(actor))
            val center = assertNotNull(world.get<Camera>(camera)).lens.center
            return FrameObservation(
                actorWorld = Vec3f(actorMatrix.m03, actorMatrix.m13, actorMatrix.m23),
                mountWorld = Vec3f(mountMatrix.m03, mountMatrix.m13, mountMatrix.m23),
                cameraCenter = Vec3f(center.x, center.y, center.z),
                locomotionClip = assertNotNull(world.get<LocomotionAnimation>(actor)).playing,
                activeClip = animator.player.activeClipId,
                playbackSpeed = animator.player.speed,
                clipTime = animator.player.time,
            )
        }
    }

    private class NamedCloseableSystem(
        val label: String,
        private val closed: MutableList<String>,
    ) : System,
        AutoCloseable {
        override fun update(world: World, delta: Float) = Unit

        override fun close() {
            closed += label
        }
    }

    private class TrackingCapability(
        override val id: String,
        private val closed: MutableList<String>,
        private val preName: String? = null,
        private val frameName: String? = null,
    ) : SceneFeature {
        override fun plan(scene: SceneDocument, plan: SceneSystemPlan) {
            preName?.let { name -> plan.preAnimation(name) { NamedCloseableSystem(name, closed) } }
            frameName?.let { name -> plan.frame(name) { NamedCloseableSystem(name, closed) } }
        }
    }

    private fun system(block: (World, Float) -> Unit): System = object : System {
        override fun update(world: World, delta: Float) = block(world, delta)
    }

    private fun capability(id: String, configure: (SceneSystemPlan) -> Unit): SceneFeature = object : SceneFeature {
        override val id = id

        override fun plan(scene: SceneDocument, plan: SceneSystemPlan) = configure(plan)
    }

    private fun headlessServices(): SceneHostServices = SceneHostServices.headless(
        input = { GameplayInput(Input().currentSnapshot, InputOwnership()) },
    )

    private fun projectFiles(scene: String): AssetSource {
        val entries = mapOf(
            "awake.project.json" to MANIFEST,
            "scenes/main.scene.json" to scene,
            MODEL to skinnedActorGltf(),
        )
        return AssetSource { path -> runCatching { entries.getValue(path.value).encodeToByteArray() } }
    }

    private class TestRenderer :
        NoopRenderer(),
        GpuDrawPreparationSource {
        override val gpuDrawPreparer = GpuDrawPreparer { _, _, _ -> null }
    }

    private companion object {
        const val DELTA = 1f / 60f
        const val WIDTH = 800f
        const val HEIGHT = 600f
        const val MODEL = "models/actor.gltf"
        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""
        const val SIMPLE_SCENE = """
{ "version": 1, "name": "harbor", "nodes": [
  { "name": "Camera", "components": [ { "component": "camera", "isPrimary": true } ] }
] }
"""
        const val CHILD_TARGET_SCENE = """
{ "version": 1, "name": "harbor", "nodes": [
  { "name": "Camera", "components": [
    { "component": "camera", "isPrimary": true },
    { "component": "camera_rig", "mode": "TopDown", "target": "Mount", "offset": { "x": 0.0, "y": 0.0, "z": 0.0 } }
  ] },
  { "name": "Actor", "components": [
    { "component": "meshRenderer", "mesh": "$MODEL", "material": "skinned-material" },
    { "component": "locomotion_animation", "idle": "stand", "walk": "walk", "run": "run",
      "walkAbove": 0.1, "runAbove": 0.8, "crossFade": 0.0, "walkReferenceSpeed": 1.5, "runReferenceSpeed": 4.0 }
  ], "children": [
    { "name": "Mount", "transform": { "position": { "x": 0.0, "y": 1.6, "z": 0.0 } } }
  ] }
] }
"""
        const val DIRECT_TARGET_SCENE = """
{ "version": 1, "name": "harbor", "nodes": [
  { "name": "Camera", "components": [
    { "component": "camera", "isPrimary": true },
    { "component": "camera_rig", "mode": "Cinematic", "target": "Actor", "offset": { "x": 0.0, "y": 0.0, "z": 0.0 } }
  ] },
  { "name": "Actor", "components": [
    { "component": "meshRenderer", "mesh": "$MODEL", "material": "skinned-material" },
    { "component": "locomotion_animation", "idle": "stand", "walk": "walk", "run": "run",
      "walkAbove": 0.1, "runAbove": 0.8, "crossFade": 0.0, "walkReferenceSpeed": 1.5, "runReferenceSpeed": 4.0 }
  ] }
] }
"""

        fun World.findEntityNamed(name: String): Entity? {
            var match: Entity? = null
            queryEach(Name::class) { entity, value -> if (value.value == name) match = entity }
            return match
        }

        fun World.entityNamed(name: String): Entity =
            checkNotNull(findEntityNamed(name)) { "Entity '$name' was not found" }

        fun skinnedActorGltf(): String {
            val positions = floats(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f)
            val joints = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0)
            val weights = floats(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f)
            val indices = byteArrayOf(0, 0, 1, 0, 2, 0, 0, 0)
            val identity = floats(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
            val inverseBind = identity + floats(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, -1f, 0f, 1f)
            val times = floats(0f, 1f)
            val rotations = floats(0f, 0f, 0f, 1f, 0f, 0f, 0.7071f, 0.7071f)
            val chunks = listOf(positions, joints, weights, indices, inverseBind, times, rotations)
            val buffer = chunks.reduce(ByteArray::plus)
            val offsets = chunks.runningFold(0) { offset, chunk -> offset + chunk.size }
            fun view(index: Int) = """{"buffer":0,"byteOffset":${offsets[index]},"byteLength":${chunks[index].size}}"""
            fun clip(name: String) =
                """{"name":"$name","channels":[{"sampler":0,"target":{"node":1,"path":"rotation"}}],"samplers":[{"input":5,"output":6,"interpolation":"LINEAR"}]}"""
            val clips = listOf("stand", "walk", "run").joinToString(",") { clip(it) }
            return """
            {
              "asset": {"version": "2.0"},
              "buffers": [{"uri": "data:application/octet-stream;base64,${Base64.encode(buffer)}", "byteLength": ${buffer.size}}],
              "bufferViews": [${chunks.indices.joinToString(",") { view(it) }}],
              "accessors": [
                {"bufferView": 0, "componentType": 5126, "count": 3, "type": "VEC3", "min": [0,0,0], "max": [1,1,0]},
                {"bufferView": 1, "componentType": 5121, "count": 3, "type": "VEC4"},
                {"bufferView": 2, "componentType": 5126, "count": 3, "type": "VEC4"},
                {"bufferView": 3, "componentType": 5123, "count": 3, "type": "SCALAR"},
                {"bufferView": 4, "componentType": 5126, "count": 2, "type": "MAT4"},
                {"bufferView": 5, "componentType": 5126, "count": 2, "type": "SCALAR"},
                {"bufferView": 6, "componentType": 5126, "count": 2, "type": "VEC4"}
              ],
              "meshes": [{"primitives": [{"attributes": {"POSITION": 0, "JOINTS_0": 1, "WEIGHTS_0": 2}, "indices": 3}]}],
              "nodes": [
                {"name": "Root", "children": [1]},
                {"name": "Tip", "translation": [0, 1, 0]},
                {"name": "Body", "mesh": 0, "skin": 0}
              ],
              "skins": [{"inverseBindMatrices": 4, "joints": [0, 1]}],
              "animations": [$clips],
              "scenes": [{"nodes": [0, 2]}],
              "scene": 0
            }
            """.trimIndent()
        }

        fun floats(vararg values: Float): ByteArray {
            val bytes = ByteArray(values.size * 4)
            values.forEachIndexed { index, value ->
                val bits = value.toRawBits()
                for (byte in 0 until 4) bytes[index * 4 + byte] = (bits ushr (byte * 8)).toByte()
            }
            return bytes
        }
    }
}

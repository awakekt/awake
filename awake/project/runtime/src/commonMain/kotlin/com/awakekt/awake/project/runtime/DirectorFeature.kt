/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.audio.AudioClip
import com.awakekt.awake.core.audio.AudioPlayer
import com.awakekt.awake.core.audio.WavDecoder
import com.awakekt.awake.core.input.InputTake
import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.director.AudioCueSpec
import com.awakekt.awake.director.DirectorScript
import com.awakekt.awake.director.InputTakeJson
import com.awakekt.awake.director.StageRayHit
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.BodyHandle
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.director.DirectorSceneComponents
import com.awakekt.awake.scene.director.DirectorSystem
import com.awakekt.awake.scene.director.InputReplaySession
import com.awakekt.awake.scene.director.SceneCameraDirector
import com.awakekt.awake.scene.director.StageSightlineProbe
import com.awakekt.awake.scene.director.WorldStageQuery
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.physics.PhysicsBody
import com.awakekt.awake.scene.physics.PhysicsSystem
import com.awakekt.awake.scene.physics.character.KinematicCharacterController
import com.awakekt.awake.scene.runtime.SceneSystemRole
import kotlin.math.sqrt

/**
 * Host-supplied runtime override allowing headless trailer capture (`awake capture`), storyboard
 * rendering (`awake storyboard`), or game tests to direct any project scene with an external
 * [script], optional gameplay [inputTake], and optional custom [audioPlayer] (such as
 * `OfflineAudioMixer`) without mutating the scene document on disk.
 *
 * @property script Director script to execute over the scene.
 * @property inputTake Optional recorded gameplay input take replayed alongside direction.
 * @property audioPlayer Optional audio player shared by both scene `AudioSystem` and `DirectorSystem`.
 * @property extraAudioClips Pre-decoded audio clips (keyed by path) available to the director.
 * @property aspectRatioProvider Supplies the target viewport aspect ratio (`width / height`).
 * @property loop Whether playback loops when [script] completes.
 */
class DirectorRuntimeOverride(
    val script: DirectorScript,
    val inputTake: InputTake? = null,
    val audioPlayer: AudioPlayer? = null,
    val extraAudioClips: Map<String, AudioClip> = emptyMap(),
    val aspectRatioProvider: () -> Float = { DEFAULT_ASPECT },
    val loop: Boolean = false,
) {
    /** Active [DirectorSystem] bound once the scene starts, for reading poses and framing telemetry. */
    var activeDirectorSystem: DirectorSystem? = null
        internal set

    /** Active [InputReplaySession] when [inputTake] is non-null. */
    var activeReplaySession: InputReplaySession? = null
        internal set

    private companion object {
        const val DEFAULT_ASPECT = 16f / 9f
    }
}

/**
 * Internal host hooks provided to [DirectorFeature] when running under `runProject` so in-scene
 * `camera_director` components can modulate `timeScale` and clean-feed HUD visibility cleanly.
 */
internal class DirectorHostHooks(
    val onTimeScaleChanged: (Float) -> Unit,
    val onHudVisibilityChanged: (Boolean) -> Unit,
    val aspectRatioProvider: () -> Float = { DEFAULT_ASPECT },
) {
    private companion object {
        const val DEFAULT_ASPECT = 16f / 9f
    }
}

/** Loaded external director scripts, takes, and audio clips for in-scene `camera_director` components. */
internal class DirectorSceneAssets(
    val scripts: Map<String, DirectorScript>,
    val takes: Map<String, InputTake>,
    val audioClips: Map<String, AudioClip>,
)

/**
 * Core feature registering `:awake:scene:director`'s components (`camera_director`, `virtual_camera`,
 * `camera_rail`, `stage_marker`), preloading referenced scripts/audio clips, and planning
 * `DirectorSystem` when a scene authors a `camera_director`.
 */
internal object DirectorFeature : SceneFeature {
    override val id: String = "com.awakekt.awake.director"
    override val components = DirectorSceneComponents.bindings
    override val systemNames: List<String> = listOf(CoreSystems.INPUT_REPLAY, CoreSystems.DIRECTOR)

    internal val AssetsKey: SceneContentKey<DirectorSceneAssets> = SceneContentKey("director assets")

    override suspend fun load(scene: SceneDocument, files: AssetSource, content: SceneContent.Builder) {
        val directors = collectDirectors(scene.nodes)
        if (directors.isEmpty()) return

        val scripts = LinkedHashMap<String, DirectorScript>()
        val takes = LinkedHashMap<String, InputTake>()
        val audioPaths = LinkedHashSet<String>()

        for (comp in directors) {
            val loadedScript = comp.inlineScript ?: comp.scriptPath?.let { path ->
                scripts.getOrPut(path) {
                    val text = files.readText(path)
                    DirectorScript.decode(text)
                }
            }
            if (loadedScript != null) {
                loadedScript.music?.path?.let(audioPaths::add)
                loadedScript.audioTrack.filterIsInstance<AudioCueSpec.PlaySfx>().forEach { audioPaths.add(it.path) }
                val takePath = comp.inputTakePath ?: loadedScript.inputTake
                if (!takePath.isNullOrBlank() && takePath !in takes) {
                    takes[takePath] = InputTakeJson.decode(files.readText(takePath))
                }
            }
        }

        val clips = LinkedHashMap<String, AudioClip>()
        for (path in audioPaths) {
            val bytes = files.read(AssetPath(path)).getOrNull() ?: continue
            WavDecoder.decode(path, path, bytes)?.let { clips[path] = it }
        }
        content[AssetsKey] = DirectorSceneAssets(scripts, takes, clips)
    }

    override fun plan(scene: SceneDocument, plan: SceneSystemPlan) {
        if (!scene.uses(SceneCameraDirector::class)) return
        plan.frame(CoreSystems.DIRECTOR, SceneSystemRole.Presentation) { services ->
            val assets = services.content[AssetsKey]
            val clips = buildMap {
                services.content[CoreSceneContent.AudioClips]?.let(::putAll)
                assets?.audioClips?.let(::putAll)
            }
            val probes = SceneCameraCollisionProbes(
                services.physics,
                services.content[StreamedTerrainFeature.Terrain]?.terrain?.heights,
            )
            val sightline = if (plan.hasPhysics && services.physics != null) {
                PhysicsStageSightlineProbe(services.physics, plan.physicsSystem(services))
            } else {
                StageSightlineProbe.None
            }
            val hooks = services.optional(DirectorHostHooks::class)
            DirectorSystem(
                stageQuery = WorldStageQuery(collisionProbes = probes, sightlineProbe = sightline),
                audioPlayer = services.audio,
                audioClips = clips,
                loadedScripts = assets?.scripts.orEmpty(),
                aspectRatioProvider = hooks?.aspectRatioProvider ?: { DEFAULT_ASPECT },
                onTimeScaleChanged = hooks?.onTimeScaleChanged ?: {},
                onHudVisibilityChanged = hooks?.onHudVisibilityChanged ?: {},
            )
        }
    }

    private fun collectDirectors(nodes: List<SceneNode>): List<SceneCameraDirector> = buildList {
        fun visit(node: SceneNode) {
            node.components.filterIsInstance<SceneCameraDirector>().forEach(::add)
            node.children.forEach(::visit)
        }
        nodes.forEach(::visit)
    }

    private const val DEFAULT_ASPECT = 16f / 9f
}

/**
 * Sightline raycast probe backed by Awake's [PhysicsWorld], ignoring the subject's own physics body
 * or character controller and resolving the blocking entity's `Name` when hit.
 */
internal class PhysicsStageSightlineProbe(
    private val physics: PhysicsWorld,
    private val physicsSystem: PhysicsSystem?,
) : StageSightlineProbe {
    private var world: World? = null
    private val dir = Vec3f()

    /** Binds the active [World] for body handle & name lookups. */
    override fun prepare(world: World) {
        this.world = world
    }

    override fun cast(from: Vec3f, to: Vec3f, ignoreEntity: Entity?, outHit: StageRayHit): Boolean {
        outHit.reset()
        dir.set(to).sub(from)
        val length = sqrt(dir.x * dir.x + dir.y * dir.y + dir.z * dir.z)
        var occluded = false
        if (length > MIN_RAY_LEN) {
            dir.scale(1f / length)
            val currentWorld = world
            val ignoreHandle: BodyHandle? = if (ignoreEntity != null && currentWorld != null) {
                currentWorld.get<PhysicsBody>(ignoreEntity)?.handle
                    ?: currentWorld.get<KinematicCharacterController>(ignoreEntity)?.body
            } else {
                null
            }
            val hit = runCatching {
                physics.raycast(from, dir, length, onlyLayer = null, ignore = ignoreHandle)
            }.getOrElse {
                physics.raycast(from, dir, length, onlyLayer = null)
            }
            if (hit != null) {
                val fraction = (hit.distance / length).coerceIn(0f, 1f)
                val hitEntity = physicsSystem?.entityFor(hit.handle)
                if (fraction < CLEAR_THRESHOLD && (hitEntity == null || hitEntity != ignoreEntity)) {
                    outHit.occluded = true
                    outHit.fraction = fraction
                    outHit.occluderName = if (hitEntity != null && currentWorld != null) {
                        currentWorld.get<Name>(hitEntity)?.value
                    } else {
                        null
                    }
                    occluded = true
                }
            }
        }
        return occluded
    }

    private companion object {
        const val MIN_RAY_LEN = 1e-4f
        const val CLEAR_THRESHOLD = 0.98f
    }
}

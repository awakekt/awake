/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.director

import com.awakekt.awake.core.audio.AudioClip
import com.awakekt.awake.core.audio.AudioPlayer
import com.awakekt.awake.core.audio.OfflineAudioMixer
import com.awakekt.awake.core.camera.CameraOptics
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.lerp
import com.awakekt.awake.director.ActionCueSpec
import com.awakekt.awake.director.AudioCueSpec
import com.awakekt.awake.director.DirectedAtmosphereState
import com.awakekt.awake.director.DirectedCameraPose
import com.awakekt.awake.director.DirectorCueSink
import com.awakekt.awake.director.DirectorPlayer
import com.awakekt.awake.director.DirectorScript
import com.awakekt.awake.director.MusicTrackSpec
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.UnscaledSystem
import com.awakekt.awake.ecs.World
import com.awakekt.awake.particles.ParticleEmitter
import com.awakekt.awake.render.effects.BloomEffect
import com.awakekt.awake.render.effects.PostProcessingEffect
import com.awakekt.awake.render.effects.ToneMappingGradeEffect
import com.awakekt.awake.render.effects.ToneMappingMode
import com.awakekt.awake.scene.audio.AudioListener
import com.awakekt.awake.scene.controls.input.inputActions
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.animation.Animator
import com.awakekt.awake.scene.rendering.camera.Camera
import com.awakekt.awake.scene.rendering.fog.Fog
import com.awakekt.awake.scene.rendering.light.DayCycle
import com.awakekt.awake.scene.rendering.light.Light
import com.awakekt.awake.scene.rendering.postprocessing.PostProcessing
import com.awakekt.awake.scene.rendering.sky.Skybox
import com.awakekt.awake.scene.rendering.tonemapping.ToneMapping
import kotlin.math.asin
import kotlin.math.atan2

/**
 * ECS system running [DirectorPlayer] on unscaled time ([UnscaledSystem]), writing camera poses
 * onto a dedicated primary [Camera] entity while preserving the player's `ActiveCamera` gameplay
 * basis, syncing 3D [AudioListener] position/orientation, orchestrating lighting/atmosphere/grade
 * shifts and pooled dynamic practical point lights, and dispatching audio, animation, particle,
 * actor, input-action, HUD-visibility, and simulation `timeScale` cues.
 *
 * @property stageQuery Spatial query adapter resolving entities, bounds, rails, and collision probes.
 * @param audioPlayer Optional audio player (live platform player or [OfflineAudioMixer] in capture).
 * @param audioClips Loaded audio clips keyed by project path.
 * @param loadedScripts Loaded external `*.director.json` scripts keyed by project path.
 * @param overrideScript Optional host-supplied [DirectorScript] directing the scene without
 *   modifying the scene document (used by `awake storyboard` and `awake capture`).
 * @param loopOverride Whether [overrideScript] loops upon finishing.
 * @param aspectRatioProvider Supplies current viewport aspect ratio (`width / height`).
 * @param onTimeScaleChanged Callback updating the host schedule's `timeScale` (`1f` = normal).
 * @param onHudVisibilityChanged Callback toggling clean-feed HUD visibility (`drawsSceneCanvas`).
 */
@Suppress("LongParameterList", "TooManyFunctions")
class DirectorSystem(
    val stageQuery: WorldStageQuery = WorldStageQuery(),
    private val audioPlayer: AudioPlayer? = null,
    private val audioClips: Map<String, AudioClip> = emptyMap(),
    private val loadedScripts: Map<String, DirectorScript> = emptyMap(),
    private val overrideScript: DirectorScript? = null,
    private val loopOverride: Boolean = false,
    private val aspectRatioProvider: () -> Float = { DEFAULT_ASPECT },
    private val onTimeScaleChanged: (Float) -> Unit = {},
    private val onHudVisibilityChanged: (Boolean) -> Unit = {},
) : UnscaledSystem,
    AutoCloseable {
    /** Latest evaluated camera pose (readable by storyboards, analyzers, and capture harnesses). */
    val lastPose: DirectedCameraPose = DirectedCameraPose()

    /** Latest evaluated lighting/atmosphere/color-grade state. */
    val lastAtmosphere: DirectedAtmosphereState = DirectedAtmosphereState()

    /** Currently active [DirectorPlayer], or `null` when no director script is playing. */
    var activePlayer: DirectorPlayer? = null
        private set

    private var activeSourceEntity: Entity? = null
    private var activeScriptRef: DirectorScript? = null
    private var directorCameraEntity: Entity? = null
    private var previousPrimaryCamera: Entity? = null
    private var previousAudioListener: Entity? = null
    private var lastReportedTimeScale: Float = 1f
    private var lastAppliedHudVisible: Boolean? = null

    private val dynamicLightEntities = Array<Entity?>(DirectedAtmosphereState.MAX_DYNAMIC_LIGHTS) { null }
    private val heldInputActions = LinkedHashSet<String>()
    private val activeActorMoves = mutableListOf<ActiveActorMove>()
    private val emitterBaseRates = HashMap<Int, Float>()

    // Scratch vectors avoiding per-frame allocations.
    private val scratchForward = Vec3f()
    private val scratchRight = Vec3f()
    private val scratchUp = Vec3f()
    private val scratchPos = Vec3f()
    private val scratchTarget = Vec3f()

    private val cueSink = object : DirectorCueSink {
        private var currentWorld: World? = null

        fun bind(world: World) {
            currentWorld = world
        }

        override fun onStartMusic(music: MusicTrackSpec) {
            val player = audioPlayer ?: return
            val clip = audioClips[music.path] ?: return
            if (player is OfflineAudioMixer) {
                player.playMusic(
                    clip = clip,
                    volume = music.volume,
                    loop = music.loop,
                    startOffsetSeconds = music.startOffsetSeconds,
                    fadeInSeconds = music.fadeInSeconds,
                )
            } else {
                player.playMusic(clip, volume = music.volume, loop = music.loop)
            }
        }

        override fun onMusicState(cue: AudioCueSpec.MusicControl) {
            val player = audioPlayer ?: return
            if (cue.stop) {
                player.stopMusic()
                return
            }
            if (player is OfflineAudioMixer) {
                cue.targetVolume?.let { player.fadeMusicTo(it, cue.fadeSeconds) }
                cue.duckGain?.let { player.triggerDuck(it, cue.duckHoldSeconds) }
            } else {
                cue.targetVolume?.let { player.musicVolume = it }
            }
        }

        override fun onPlaySfx(cue: AudioCueSpec.PlaySfx) {
            val player = audioPlayer ?: return
            val clip = audioClips[cue.path] ?: return
            val world = currentWorld
            if (player is OfflineAudioMixer && cue.duckMusicGain != null) {
                player.triggerDuck(cue.duckMusicGain!!, cue.duckHoldSeconds)
            }
            if (cue.maxDistance > 0f) {
                val emitterPos = resolveAudioPosition(world, cue)
                CameraOptics.cameraBasis(
                    lastPose.eye,
                    lastPose.center,
                    lastPose.rollRadians,
                    scratchForward,
                    scratchRight,
                    scratchUp,
                )
                player.playSound3D(
                    clip = clip,
                    emitterPos = emitterPos,
                    listenerPos = lastPose.eye,
                    listenerRight = scratchRight,
                    maxDistance = cue.maxDistance,
                    loop = false,
                )
            } else if (player is OfflineAudioMixer) {
                player.playSoundPitched(clip = clip, volume = cue.volume, pitch = cue.pitch)
            } else {
                player.playSound(clip = clip, volume = cue.volume, loop = false)
            }
        }

        override fun onInputAction(action: String, active: Boolean, durationSeconds: Float) {
            if (active) {
                heldInputActions.add(action)
            } else {
                heldInputActions.remove(action)
            }
        }

        override fun onActorCue(cue: ActionCueSpec.ActorMove) {
            val world = currentWorld
            val entity = if (world != null) stageQuery.findEntity(cue.entity) else null
            val transform = if (world != null && entity != null) world.get<Transform>(entity) else null
            if (world != null && entity != null && transform != null) {
                val dest = Vec3f(transform.position.x, transform.position.y, transform.position.z)
                when {
                    cue.targetPosition != null -> cue.targetPosition!!.copyTo(dest)
                    cue.targetMarker != null -> {
                        val marker = stageQuery.findEntity(cue.targetMarker!!)
                        marker?.let { world.get<Transform>(it) }?.let { mt ->
                            dest.set(mt.position)
                        }
                    }
                }
                val duration = activePlayer?.script?.clock?.resolveDuration(cue.duration) ?: 1f
                activeActorMoves.add(
                    ActiveActorMove(
                        entity = entity,
                        start = Vec3f(transform.position.x, transform.position.y, transform.position.z),
                        end = dest,
                        faceTargetName = cue.faceTarget,
                        duration = duration.coerceAtLeast(MIN_MOVE_DURATION),
                        easing = cue.easing,
                        elapsed = 0f,
                    ),
                )
            }
        }

        override fun onAnimationCue(cue: ActionCueSpec.PlayAnimation) {
            val world = currentWorld
            val entity = if (world != null) stageQuery.findEntity(cue.entity) else null
            val animator = if (world != null && entity != null) world.get<Animator>(entity) else null
            if (animator != null) {
                val playback = if (cue.loop) {
                    com.awakekt.awake.core.animation.AnimationPlayback.Loop
                } else {
                    com.awakekt.awake.core.animation.AnimationPlayback.Once
                }
                animator.player.speed = cue.speed.coerceAtLeast(0f)
                if (cue.clip in animator.player.clipEntries) {
                    if (cue.crossfadeSeconds > 0f) {
                        animator.player.crossFadeTo(cue.clip, cue.crossfadeSeconds, playback)
                    } else {
                        animator.player.play(cue.clip, playback)
                    }
                }
            }
        }

        override fun onParticleCue(cue: ActionCueSpec.ParticleBurst) {
            val world = currentWorld
            val entity = if (world != null) stageQuery.findEntity(cue.entity) else null
            val emitter = if (world != null && entity != null) world.get<ParticleEmitter>(entity) else null
            if (entity != null && emitter != null) {
                if (!emitterBaseRates.containsKey(entity.id)) {
                    emitterBaseRates[entity.id] = emitter.spawnRate
                }
                emitter.spawnRate = if (cue.enabled) emitterBaseRates[entity.id] ?: emitter.spawnRate else 0f
                if (cue.burstCount > 0) {
                    emitter.emit(cue.burstCount)
                }
            }
        }

        override fun onHudVisibility(visible: Boolean) {
            if (lastAppliedHudVisible != visible) {
                lastAppliedHudVisible = visible
                onHudVisibilityChanged(visible)
            }
        }
    }

    override fun update(world: World, delta: Float) {
        val player = resolveActivePlayer(world)
        if (player == null) {
            releaseDirectorTakeover(world)
            return
        }

        stageQuery.prepare(world, delta)
        cueSink.bind(world)
        applyHeldInputActions(world)

        player.step(
            unscaledDelta = delta,
            aspectRatio = aspectRatioProvider().coerceAtLeast(MIN_ASPECT),
            stage = stageQuery,
            outPose = lastPose,
            outAtmosphere = lastAtmosphere,
            cueSink = cueSink,
        )

        stepActorMoves(world, delta * player.currentTimeScale)
        applyCameraPose(world, lastPose)
        applyAtmosphere(world, lastAtmosphere)

        if (player.currentTimeScale != lastReportedTimeScale) {
            lastReportedTimeScale = player.currentTimeScale
            onTimeScaleChanged(player.currentTimeScale)
        }
    }

    /** Restores the original primary camera, clears dynamic lights, and resets `timeScale` to `1f`. */
    fun releaseDirectorTakeover(world: World) {
        if (lastReportedTimeScale != 1f) {
            lastReportedTimeScale = 1f
            onTimeScaleChanged(1f)
        }
        heldInputActions.clear()
        activeActorMoves.clear()

        for (i in dynamicLightEntities.indices) {
            val entity = dynamicLightEntities[i]
            if (entity != null && world.isAlive(entity)) {
                world.destroy(entity)
            }
            dynamicLightEntities[i] = null
        }

        val dirCam = directorCameraEntity
        if (dirCam != null && world.isAlive(dirCam)) {
            world.get<Camera>(dirCam)?.isPrimary = false
            if (world.has(dirCam, AudioListener::class)) {
                world.remove(dirCam, AudioListener::class)
            }
        }

        val prevPrimary = previousPrimaryCamera
        if (prevPrimary != null && world.isAlive(prevPrimary)) {
            world.get<Camera>(prevPrimary)?.isPrimary = true
        }
        previousPrimaryCamera = null

        val prevListener = previousAudioListener
        if (prevListener != null && world.isAlive(prevListener) && !world.has(prevListener, AudioListener::class)) {
            world.add(prevListener, AudioListener())
        }
        previousAudioListener = null
    }

    override fun close() {
        heldInputActions.clear()
        activeActorMoves.clear()
        if (lastReportedTimeScale != 1f) {
            lastReportedTimeScale = 1f
            onTimeScaleChanged(1f)
        }
    }

    private fun resolveActivePlayer(world: World): DirectorPlayer? {
        if (overrideScript != null) {
            if (activeScriptRef !== overrideScript) {
                activeScriptRef = overrideScript
                activeSourceEntity = null
                activePlayer = DirectorPlayer(overrideScript, loop = loopOverride)
            }
            return activePlayer
        }

        var chosenEntity: Entity? = null
        var chosenComp: CameraDirectorComponent? = null
        world.queryEach(CameraDirectorComponent::class) { entity, comp ->
            if (!comp.playing) return@queryEach
            if (comp.resolvedScript == null) {
                val path = comp.authored.scriptPath
                if (path != null) {
                    comp.resolvedScript = loadedScripts[path]
                }
            }
            if (comp.resolvedScript == null) return@queryEach
            if (chosenComp == null || comp.priority > chosenComp.priority) {
                chosenEntity = entity
                chosenComp = comp
            }
        }

        val comp = chosenComp
        val script = comp?.resolvedScript
        if (comp == null || script == null) {
            activePlayer = null
            activeSourceEntity = null
            activeScriptRef = null
        } else if (chosenEntity != activeSourceEntity || script !== activeScriptRef) {
            activeSourceEntity = chosenEntity
            activeScriptRef = script
            activePlayer = DirectorPlayer(script, loop = comp.loop)
        }
        return activePlayer
    }

    private fun applyCameraPose(world: World, pose: DirectedCameraPose) {
        val dirEntity = ensureDirectorCamera(world)
        val camera = world.get<Camera>(dirEntity) ?: return
        val lens = camera.lens
        lens.eye.set(pose.eye)
        lens.center.set(pose.center)
        lens.up.set(pose.up)
        lens.fovYRadians = pose.fovYRadians
        lens.near = pose.near
        lens.far = pose.far
        camera.isPrimary = true

        // Sync Transform so AudioListener spatializes 3D SFX from the directed camera's exact eye & right vector.
        val transform = world.get<Transform>(dirEntity)
        if (transform != null) {
            transform.position.set(pose.eye)
            CameraOptics.cameraBasis(pose.eye, pose.center, pose.rollRadians, scratchForward, scratchRight, scratchUp)
            val yaw = atan2(scratchForward.x, -scratchForward.z)
            val pitch = asin(scratchForward.y.coerceIn(-1f, 1f))
            transform.rotation.set(pitch, yaw, pose.rollRadians)
            // Keep worldMatrix right column fresh immediately even before TransformSystem runs.
            transform.worldMatrix.setEulerTRS(
                transform.position.x,
                transform.position.y,
                transform.position.z,
                transform.rotation.x,
                transform.rotation.y,
                transform.rotation.z,
                1f,
                1f,
                1f,
            )
        }
    }

    private fun ensureDirectorCamera(world: World): Entity {
        val existing = directorCameraEntity
        if (existing != null && world.isAlive(existing) && world.has(existing, Camera::class)) {
            ensureExclusivePrimaryAndListener(world, existing)
            return existing
        }

        // Remember current primary camera and audio listener so we restore them cleanly on stop.
        world.queryEach(Camera::class) { entity, camera ->
            if (camera.isPrimary && previousPrimaryCamera == null) {
                previousPrimaryCamera = entity
            }
            camera.isPrimary = false
        }
        world.queryEach(AudioListener::class) { entity, _ ->
            if (previousAudioListener == null) previousAudioListener = entity
        }

        val entity = world.create()
        world.add(entity, Name(DIRECTOR_CAMERA_NAME))
        world.add(entity, Transform(position = Vec3f(lastPose.eye.x, lastPose.eye.y, lastPose.eye.z)))
        world.add(
            entity,
            Camera(
                lens = Lens(
                    eye = Vec3f(lastPose.eye.x, lastPose.eye.y, lastPose.eye.z),
                    center = Vec3f(lastPose.center.x, lastPose.center.y, lastPose.center.z),
                    up = Vec3f(lastPose.up.x, lastPose.up.y, lastPose.up.z),
                    fovYRadians = lastPose.fovYRadians,
                    near = lastPose.near,
                    far = lastPose.far,
                ),
                isPrimary = true,
            ),
        )
        world.add(entity, AudioListener())
        directorCameraEntity = entity
        ensureExclusivePrimaryAndListener(world, entity)
        return entity
    }

    private fun ensureExclusivePrimaryAndListener(world: World, dirEntity: Entity) {
        world.queryEach(Camera::class) { entity, camera ->
            camera.isPrimary = entity == dirEntity
        }
        // Keep AudioListener exclusively on the directed camera while directing.
        var staleListener: Entity? = null
        world.queryEach(AudioListener::class) { entity, _ ->
            if (entity != dirEntity) staleListener = entity
        }
        staleListener?.let { world.remove(it, AudioListener::class) }
        if (!world.has(dirEntity, AudioListener::class)) {
            world.add(dirEntity, AudioListener())
        }
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun applyAtmosphere(world: World, atmo: DirectedAtmosphereState) {
        if (atmo.hasDayTime) {
            world.queryEach(DayCycle::class) { _, dayCycle ->
                dayCycle.time = atmo.dayTime
            }
        }
        if (atmo.hasDirectionalLight) {
            var appliedSun = false
            world.queryEach(Light::class) { _, light ->
                if (!appliedSun && light.type == Light.Type.Directional) {
                    appliedSun = true
                    light.color.set(atmo.sunColor)
                    light.intensity = atmo.sunIntensity
                    if (atmo.hasSunDirection) light.direction.set(atmo.sunDirection)
                    if (atmo.sunAmbient >= 0f) light.ambient = atmo.sunAmbient
                }
            }
        }
        if (atmo.hasFog) {
            world.queryEach(Fog::class) { _, fog ->
                fog.enabled = atmo.fogEnabled
                fog.density = atmo.fogDensity
                fog.color = atmo.fogAsColor()
            }
        }
        if (atmo.hasSky) {
            world.queryEach(Skybox::class) { _, skybox ->
                skybox.horizonColor = atmo.skyHorizonAsColor()
                skybox.zenithColor = atmo.skyZenithAsColor()
                skybox.exposure = atmo.skyExposure
            }
        }
        if (atmo.hasGrade) {
            world.queryEach(ToneMapping::class) { _, tm ->
                tm.exposure = atmo.exposure.coerceAtLeast(MIN_EXPOSURE)
            }
            val dirCam = directorCameraEntity
            if (dirCam != null && world.isAlive(dirCam)) {
                val effects = buildList<PostProcessingEffect> {
                    if (atmo.bloomIntensity > 0f) {
                        add(
                            BloomEffect(
                                enabled = true,
                                intensity = atmo.bloomIntensity,
                                threshold = if (atmo.bloomThreshold >= 0f) atmo.bloomThreshold else DEFAULT_BLOOM_THRESHOLD,
                            ),
                        )
                    }
                    // Passthrough mode grades display-referred scene color without double tone-mapping!
                    add(
                        ToneMappingGradeEffect(
                            enabled = true,
                            exposure = atmo.exposure.coerceAtLeast(MIN_EXPOSURE),
                            contrast = atmo.contrast.coerceAtLeast(0f),
                            saturation = atmo.saturation.coerceAtLeast(0f),
                            whiteBalanceTemperature = atmo.whiteBalanceTemperature,
                            whiteBalanceTint = atmo.whiteBalanceTint,
                            mode = ToneMappingMode.Passthrough,
                        ),
                    )
                }
                val pp = world.get<PostProcessing>(dirCam)
                if (pp != null) {
                    pp.enabled = true
                    pp.effects = effects
                } else {
                    world.add(dirCam, PostProcessing(enabled = true, effects = effects))
                }
            }
        }

        // Sync pooled dynamic director practical point lights (Hero Rim Light, Lightning, Spell Flash).
        val activeCount = atmo.activeDynamicLightCount
        for (slot in 0 until DirectedAtmosphereState.MAX_DYNAMIC_LIGHTS) {
            val entity = dynamicLightEntities[slot]
            if (slot < activeCount) {
                val src = atmo.dynamicLights[slot]
                if (entity == null || !world.isAlive(entity)) {
                    val created = world.create()
                    world.add(created, Name("$DYNAMIC_LIGHT_PREFIX$slot"))
                    world.add(created, Transform(position = Vec3f(src.position.x, src.position.y, src.position.z)))
                    world.add(
                        created,
                        Light(
                            color = Vec3f(src.color.x, src.color.y, src.color.z),
                            intensity = src.intensity,
                            type = Light.Type.Point,
                            range = src.range,
                            shadowsEnabled = false,
                        ),
                    )
                    dynamicLightEntities[slot] = created
                } else {
                    world.get<Transform>(entity)?.position?.set(src.position)
                    world.get<Light>(entity)?.let { light ->
                        light.color.set(src.color)
                        light.intensity = src.intensity
                    }
                }
            } else if (entity != null && world.isAlive(entity)) {
                world.get<Light>(entity)?.intensity = 0f
            }
        }
    }

    private fun applyHeldInputActions(world: World) {
        if (heldInputActions.isEmpty()) return
        val actions = world.inputActions() ?: return
        for (name in heldInputActions) {
            actions.hold(name)
        }
    }

    private fun stepActorMoves(world: World, simDelta: Float) {
        if (activeActorMoves.isEmpty() || simDelta <= 0f) return
        var i = 0
        while (i < activeActorMoves.size) {
            val move = activeActorMoves[i]
            if (!world.isAlive(move.entity)) {
                activeActorMoves.removeAt(i)
                continue
            }
            move.elapsed += simDelta
            val t = (move.elapsed / move.duration).coerceIn(0f, 1f)
            val eased = move.easing.evaluate(t)
            val transform = world.get<Transform>(move.entity)
            if (transform != null) {
                transform.position.set(
                    lerp(move.start.x, move.end.x, eased),
                    lerp(move.start.y, move.end.y, eased),
                    lerp(move.start.z, move.end.z, eased),
                )
                val faceName = move.faceTargetName
                if (faceName != null) {
                    val targetEnt = stageQuery.findEntity(faceName)
                    val targetT = targetEnt?.let { world.get<Transform>(it) }
                    if (targetT != null) {
                        transform.rotation.y = atan2(
                            targetT.position.x - transform.position.x,
                            -(targetT.position.z - transform.position.z),
                        )
                    }
                } else {
                    val dx = move.end.x - move.start.x
                    val dz = move.end.z - move.start.z
                    if (dx * dx + dz * dz > MIN_TURN_DIST_SQ) {
                        transform.rotation.y = atan2(dx, -dz)
                    }
                }
            }
            if (t >= 1f) {
                activeActorMoves.removeAt(i)
            } else {
                i++
            }
        }
    }

    private fun resolveAudioPosition(world: World?, cue: AudioCueSpec.PlaySfx): Vec3f {
        if (cue.entity != null && world != null) {
            val ent = stageQuery.findEntity(cue.entity!!)
            val transform = ent?.let { world.get<Transform>(it) }
            if (transform != null) return scratchPos.set(transform.position)
        }
        return cue.position?.copyTo(scratchTarget) ?: scratchPos.set(lastPose.center)
    }

    private class ActiveActorMove(
        val entity: Entity,
        val start: Vec3f,
        val end: Vec3f,
        val faceTargetName: String?,
        val duration: Float,
        val easing: com.awakekt.awake.core.math.EasingCurve,
        var elapsed: Float,
    )

    /** Constants for [DirectorSystem]. */
    companion object {
        /** Name assigned to the runtime-managed director camera entity. */
        const val DIRECTOR_CAMERA_NAME: String = "__awake_director_camera__"

        private const val DYNAMIC_LIGHT_PREFIX = "__awake_director_light_"
        private const val DEFAULT_ASPECT = 16f / 9f
        private const val MIN_ASPECT = 0.25f
        private const val MIN_MOVE_DURATION = 1e-3f
        private const val MIN_EXPOSURE = 0.01f
        private const val DEFAULT_BLOOM_THRESHOLD = 1.0f
        private const val MIN_TURN_DIST_SQ = 1e-4f
    }
}

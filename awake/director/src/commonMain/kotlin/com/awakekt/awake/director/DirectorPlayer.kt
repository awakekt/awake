/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.director

import com.awakekt.awake.core.camera.CameraCollisionSolver
import com.awakekt.awake.core.camera.CameraOptics
import com.awakekt.awake.core.math.ArcLengthSpline3f
import com.awakekt.awake.core.math.EasingCurve
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.lerp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Deterministic runtime camera, lighting, audio, and action director stepping a [DirectorScript]
 * on **unscaled timeline time** so slow-motion speed ramps never stall camera motion or drift off
 * the musical beat grid.
 *
 * All per-frame pose, framing, collision, and atmosphere evaluations reuse internal scratch objects
 * and allocate nothing inside [step].
 *
 * @property script Validated declarative script being directed.
 * @property loop Whether playback wraps back to `0s` upon reaching [totalDurationSeconds].
 */
@Suppress("LargeClass", "TooManyFunctions")
class DirectorPlayer(
    val script: DirectorScript,
    val loop: Boolean = false,
) {
    private val clock = script.clock
    private val framingSolver = FramingSolver()
    private val feelRig = CameraFeelRig(seed = script.seed)
    private val collisionSolver = CameraCollisionSolver()

    private val resolvedShots: List<ResolvedShot>
    private val resolvedLighting: List<ResolvedLightingCue>
    private val resolvedAudio: List<ResolvedAudioCue>
    private val resolvedActions: List<ResolvedActionCue>
    private val resolvedSpeedRamps: List<ResolvedSpeedRamp>

    /** Total resolved duration of the script in seconds. */
    val totalDurationSeconds: Float

    private var currentTimeDouble: Double = 0.0

    /** Current elapsed timeline time in seconds. */
    val currentTimeSeconds: Float get() = currentTimeDouble.toFloat()

    /** Current musical beat coordinate from [DirectorScript.clock]. */
    val currentBeat: Float get() = clock.beatAt(currentTimeSeconds)

    /** Zero-based index of the active shot, or `-1` if [script] has no shots. */
    var currentShotIndex: Int = if (script.shots.isNotEmpty()) 0 else -1
        private set

    /** Active shot specification, or `null` if [script] has no shots. */
    val currentShot: ShotSpec?
        get() = script.shots.getOrNull(currentShotIndex)

    /** Current simulation time scale (`1f` = normal speed, `< 1f` = slow motion, `0f` = hitstop). */
    var currentTimeScale: Float = 1f
        private set

    /** Current SFX pitch scale aligned with [currentTimeScale] when speed ramps enable audio pitch shifting. */
    var currentAudioPitchScale: Float = 1f
        private set

    /** Whether a non-looping script has reached [totalDurationSeconds]. */
    var isFinished: Boolean = false
        private set

    private var musicStarted: Boolean = false
    private var musicFadeOutTriggered: Boolean = false
    private var lastHudVisible: Boolean? = null
    private var lastEvaluatedShotIndex: Int = -1
    private var audioCueFired: BooleanArray
    private var actionCueFired: BooleanArray
    private var inputActionReleased: BooleanArray

    // Reusable per-frame scratch state (zero allocations in step()).
    private val previousShotPose = DirectedCameraPose()
    private val incomingShotPose = DirectedCameraPose()
    private val tempSubject = StageSubjectState()
    private val tempSubject2 = StageSubjectState()
    private val scratchVecA = Vec3f()
    private val scratchVecB = Vec3f()
    private val scratchForward = Vec3f()
    private val scratchRight = Vec3f()
    private val scratchUp = Vec3f()
    private val scratchSolvedEye = Vec3f()

    init {
        var cursor = 0f
        resolvedShots = script.shots.mapIndexed { index, spec ->
            val dur = clock.resolveDuration(spec.duration).coerceAtLeast(MIN_DURATION)
            val start = cursor
            cursor += dur
            val inlineSpline = if (spec.rig is ShotRigSpec.SplineRail && spec.rig.waypoints.size >= 2) {
                ArcLengthSpline3f(spec.rig.waypoints.map(DirectorVec3::toVec3f), closed = spec.rig.closed)
            } else {
                null
            }
            ResolvedShot(
                index = index,
                spec = spec,
                startTime = start,
                endTime = cursor,
                duration = dur,
                inlineSpline = inlineSpline,
                collision = spec.collision?.toCameraCollision(),
            )
        }
        totalDurationSeconds = cursor

        resolvedLighting = script.lightingTrack.mapIndexed { idx, cue ->
            val start = clock.resolveTime(cue.at)
            val dur = when (cue) {
                is LightingCueSpec.DayCycleSweep -> clock.resolveDuration(cue.duration)
                is LightingCueSpec.DirectionalLight -> clock.resolveDuration(cue.duration)
                is LightingCueSpec.HeroRimLight -> clock.resolveDuration(cue.duration)
                is LightingCueSpec.LightFlash -> clock.resolveDuration(cue.duration)
                is LightingCueSpec.FogRamp -> clock.resolveDuration(cue.duration)
                is LightingCueSpec.ColorGrade -> clock.resolveDuration(cue.duration)
            }.coerceAtLeast(MIN_DURATION)
            val slotId = when (cue) {
                is LightingCueSpec.HeroRimLight -> "rim:${cue.subject}:$idx"
                is LightingCueSpec.LightFlash -> "flash:$idx"
                else -> ""
            }
            ResolvedLightingCue(cue, start, start + dur, dur, slotId)
        }

        resolvedAudio = script.audioTrack.map { cue ->
            ResolvedAudioCue(cue, clock.resolveTime(cue.at))
        }
        resolvedActions = script.actionTrack.map { cue ->
            val start = clock.resolveTime(cue.at)
            val end = if (cue is ActionCueSpec.InputAction) {
                start + clock.resolveDuration(cue.duration)
            } else {
                start
            }
            ResolvedActionCue(cue, start, end)
        }
        resolvedSpeedRamps = script.actionTrack.mapNotNull { cue ->
            if (cue is ActionCueSpec.TimeScaleRamp) {
                ResolvedSpeedRamp(cue, clock.resolveTime(cue.at))
            } else {
                null
            }
        }

        audioCueFired = BooleanArray(resolvedAudio.size)
        actionCueFired = BooleanArray(resolvedActions.size)
        inputActionReleased = BooleanArray(resolvedActions.size)
    }

    /** Rewinds playback cleanly back to `0s`, resetting trauma, collision easing, and cue triggers. */
    fun reset() {
        currentTimeDouble = 0.0
        currentShotIndex = if (resolvedShots.isNotEmpty()) 0 else -1
        currentTimeScale = 1f
        currentAudioPitchScale = 1f
        isFinished = false
        musicStarted = false
        musicFadeOutTriggered = false
        lastHudVisible = null
        lastEvaluatedShotIndex = -1
        audioCueFired.fill(false)
        actionCueFired.fill(false)
        inputActionReleased.fill(false)
        feelRig.reset()
        collisionSolver.reset()
    }

    /** Returns the resolved start time in seconds for shot [shotIndex]. */
    fun shotStartTime(shotIndex: Int): Float = resolvedShots[shotIndex].startTime

    /** Returns the resolved duration in seconds for shot [shotIndex]. */
    fun shotDuration(shotIndex: Int): Float = resolvedShots[shotIndex].duration

    /** Finds a shot index by [shotId], or `-1` if not found. */
    fun findShotIndex(shotId: String): Int = resolvedShots.indexOfFirst { it.spec.id == shotId }

    /**
     * Advances the director timeline by [unscaledDelta] seconds and writes the evaluated camera
     * pose into [outPose] and atmosphere/lighting state into [outAtmosphere].
     *
     * @param unscaledDelta Frame duration in real unscaled seconds (`>= 0f`).
     * @param aspectRatio Active viewport aspect ratio (`width / height`).
     * @param stage Scene-agnostic spatial query provider.
     * @param outPose Destination camera pose mutated in place.
     * @param outAtmosphere Destination lighting/atmosphere/grade state mutated in place.
     * @param cueSink Sink receiving one-shot audio, action, and HUD cues.
     */
    @Suppress("LongParameterList", "CyclomaticComplexMethod", "LongMethod")
    fun step(
        unscaledDelta: Float,
        aspectRatio: Float,
        stage: StageQuery,
        outPose: DirectedCameraPose,
        outAtmosphere: DirectedAtmosphereState,
        cueSink: DirectorCueSink = DirectorCueSink.None,
    ) {
        if (resolvedShots.isEmpty()) {
            isFinished = true
            return
        }
        val dt = unscaledDelta.coerceAtLeast(0f)

        // Start top-level soundtrack on first active step.
        val music = script.music
        if (music != null && !musicStarted) {
            musicStarted = true
            cueSink.onStartMusic(music)
        }

        // Advance timeline clock in Double to eliminate 60fps Float accumulation drift over long trailers.
        if (!isFinished && dt > 0f) {
            val nextTime = currentTimeDouble + dt.toDouble()
            val totalDouble = totalDurationSeconds.toDouble()
            if (nextTime >= totalDouble - TIME_END_EPSILON) {
                if (loop && totalDouble > 0.0) {
                    currentTimeDouble = nextTime.mod(totalDouble)
                    audioCueFired.fill(false)
                    actionCueFired.fill(false)
                    inputActionReleased.fill(false)
                    lastEvaluatedShotIndex = -1
                    musicFadeOutTriggered = false
                } else {
                    currentTimeDouble = totalDouble
                    isFinished = true
                }
            } else {
                currentTimeDouble = nextTime
            }
        }

        // Trigger smooth music fade-out at tail of non-looping script.
        val shouldCheckFadeOut = music != null && !loop && !musicFadeOutTriggered
        if (shouldCheckFadeOut && music.fadeOutSeconds > 0f) {
            val fadeStart = (totalDurationSeconds - music.fadeOutSeconds).coerceAtLeast(0f)
            if (currentTimeSeconds >= fadeStart) {
                musicFadeOutTriggered = true
                cueSink.onMusicState(
                    AudioCueSpec.MusicControl(
                        at = TimeRef.seconds(currentTimeSeconds),
                        targetVolume = 0f,
                        fadeSeconds = music.fadeOutSeconds,
                    ),
                )
            }
        }

        // Dispatch timed cues whose trigger time has been reached.
        dispatchAudioCues(cueSink)
        dispatchActionCues(cueSink)
        updateTimeScale()

        // Locate active shot and evaluate pose + transition.
        val shot = findShotAt(currentTimeSeconds)
        currentShotIndex = shot.index
        val localElapsed = (currentTimeSeconds - shot.startTime).coerceIn(0f, shot.duration)
        val localProgress = (localElapsed / shot.duration).coerceIn(0f, 1f)
        val effectiveAspect = script.aspectRatio ?: aspectRatio.coerceAtLeast(MIN_ASPECT)

        // Snapshot outgoing shot when entering a new shot so blends start seamlessly.
        val shotChanged = shot.index != lastEvaluatedShotIndex
        if (shotChanged) {
            if (lastEvaluatedShotIndex >= 0) {
                previousShotPose.copyFrom(outPose)
            } else {
                evaluateShotRaw(shot, 0f, effectiveAspect, stage, previousShotPose)
            }
            if (shot.spec.transitionIn is ShotTransitionSpec.HardCut) {
                collisionSolver.reset()
            }
            lastEvaluatedShotIndex = shot.index
        }

        evaluateShotRaw(shot, localProgress, effectiveAspect, stage, incomingShotPose)

        // Apply shot transition (HardCut / SmoothBlend / FadeThroughBlack).
        var fadeBlack = 0f
        when (val trans = shot.spec.transitionIn) {
            is ShotTransitionSpec.HardCut -> {
                outPose.copyFrom(incomingShotPose)
                if (shotChanged) feelRig.snapTo(outPose)
            }

            is ShotTransitionSpec.SmoothBlend -> {
                val blendDur = clock.resolveDuration(trans.duration)
                if (blendDur > EPSILON && localElapsed < blendDur && shot.index > 0) {
                    val weight = trans.easing.evaluate((localElapsed / blendDur).coerceIn(0f, 1f))
                    outPose.copyFrom(previousShotPose)
                    outPose.blendToward(incomingShotPose, weight)
                } else {
                    outPose.copyFrom(incomingShotPose)
                }
            }

            is ShotTransitionSpec.FadeThroughBlack -> {
                outPose.copyFrom(incomingShotPose)
                if (shotChanged) feelRig.snapTo(outPose)
                val fadeInDur = clock.resolveDuration(trans.duration)
                if (fadeInDur > EPSILON && localElapsed < fadeInDur) {
                    fadeBlack = (1f - localElapsed / fadeInDur).coerceIn(0f, 1f)
                }
            }
        }

        // Check if the NEXT shot requests a fade-out at the tail of this shot.
        val nextShot = resolvedShots.getOrNull(shot.index + 1)
        val nextTrans = nextShot?.spec?.transitionIn
        if (nextTrans is ShotTransitionSpec.FadeThroughBlack) {
            val fadeOutDur = clock.resolveDuration(nextTrans.fadeOutPreviousDuration)
            val remaining = shot.endTime - currentTimeSeconds
            if (fadeOutDur > EPSILON && remaining < fadeOutDur) {
                fadeBlack = maxOf(fadeBlack, (1f - remaining / fadeOutDur).coerceIn(0f, 1f))
            }
        }
        outPose.fadeBlack = fadeBlack

        // Apply procedural camera feel (spring smoothing + handheld breathing + trauma^2 shake).
        val feel = shot.spec.feel
        val effectiveSmoothTime = if (shot.spec.rig is ShotRigSpec.ChaseFollow && feel.smoothTime <= 0f) {
            shot.spec.rig.followSmoothTime
        } else {
            feel.smoothTime
        }
        feelRig.apply(
            pose = outPose,
            unscaledDelta = dt,
            timelineSeconds = currentTimeSeconds,
            smoothTime = effectiveSmoothTime,
            handheldIntensity = feel.handheldIntensity,
            handheldFrequency = feel.handheldFrequency,
            baseTrauma = feel.baseTrauma,
        )

        // Apply optional collision/terrain clearance guard so automated shots never sink into ground.
        val collision = shot.collision
        if (collision != null) {
            collisionSolver.solve(
                pivot = outPose.center,
                desiredEye = outPose.eye,
                settings = collision,
                ground = stage.cameraGround,
                obstruction = stage.cameraObstruction,
                footprint = outPose.near,
                delta = dt,
                out = scratchSolvedEye,
            )
            outPose.eye.set(scratchSolvedEye)
            CameraOptics.cameraBasis(outPose.eye, outPose.center, outPose.rollRadians, scratchForward, scratchRight, scratchUp)
            outPose.up.set(scratchUp)
        }

        // Notify HUD visibility changes cleanly.
        if (lastHudVisible != outPose.hudVisible) {
            lastHudVisible = outPose.hudVisible
            cueSink.onHudVisibility(outPose.hudVisible)
        }

        // Evaluate shot atmosphere baseline + parallel complex lighting choreography tracks.
        evaluateAtmosphere(shot, outPose, stage, outAtmosphere)
    }

    /** Directly injects trauma shake into the feel rig (callable from live gameplay events too). */
    fun addTrauma(amount: Float, decaySeconds: Float? = null) {
        feelRig.addTrauma(amount, decaySeconds)
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun evaluateShotRaw(
        shot: ResolvedShot,
        progress: Float,
        aspectRatio: Float,
        stage: StageQuery,
        out: DirectedCameraPose,
    ) {
        val spec = shot.spec
        val lens = spec.lens
        var fovY = lens.resolvedFovYRadians()
        var rollRad = lens.rollDeg * DEG_TO_RAD

        out.near = lens.near
        out.far = lens.far
        out.hudVisible = spec.hudVisible
        out.letterboxAspect = spec.letterboxAspect
        out.fadeBlack = 0f

        when (val rig = spec.rig) {
            is ShotRigSpec.Static -> {
                if (rig.framing != null) {
                    solveFraming(
                        spec = rig.framing,
                        stage = stage,
                        fovYRadians = fovY,
                        aspectRatio = aspectRatio,
                        rollRadians = rollRad,
                        outEye = out.eye,
                        outCenter = out.center,
                        outUp = out.up,
                    )
                } else {
                    (rig.eye ?: DEFAULT_STATIC_EYE).copyTo(out.eye)
                    (rig.center ?: DirectorVec3.Zero).copyTo(out.center)
                    CameraOptics.cameraBasis(out.eye, out.center, rollRad, scratchForward, scratchRight, out.up)
                }
            }

            is ShotRigSpec.Orbit -> {
                val t = rig.easing.evaluate(progress)
                solveFraming(
                    spec = rig.framing,
                    stage = stage,
                    fovYRadians = fovY,
                    aspectRatio = aspectRatio,
                    rollRadians = rollRad,
                    azimuthDeltaDeg = lerp(rig.startAzimuthDeg, rig.endAzimuthDeg, t),
                    elevationDeltaDeg = lerp(rig.startElevationDeg, rig.endElevationDeg, t),
                    radiusMultiplier = lerp(rig.startRadiusMultiplier, rig.endRadiusMultiplier, t),
                    outEye = out.eye,
                    outCenter = out.center,
                    outUp = out.up,
                )
            }

            is ShotRigSpec.Dolly -> {
                val t = rig.easing.evaluate(progress)
                if (rig.fromEye != null && rig.toEye != null) {
                    out.eye.set(
                        lerp(rig.fromEye.x, rig.toEye.x, t),
                        lerp(rig.fromEye.y, rig.toEye.y, t),
                        lerp(rig.fromEye.z, rig.toEye.z, t),
                    )
                    if (rig.fromCenter != null && rig.toCenter != null) {
                        out.center.set(
                            lerp(rig.fromCenter.x, rig.toCenter.x, t),
                            lerp(rig.fromCenter.y, rig.toCenter.y, t),
                            lerp(rig.fromCenter.z, rig.toCenter.z, t),
                        )
                    } else if (rig.framing != null && framingSolver.resolveSubjects(rig.framing, stage, tempSubject)) {
                        tempSubject.boundsCenter(out.center)
                    } else {
                        out.center.set(0f, 0f, 0f)
                    }
                    CameraOptics.cameraBasis(out.eye, out.center, rollRad, scratchForward, scratchRight, out.up)
                } else {
                    val baseFraming = rig.framing ?: DEFAULT_FRAMING
                    solveFraming(
                        spec = baseFraming,
                        stage = stage,
                        fovYRadians = fovY,
                        aspectRatio = aspectRatio,
                        rollRadians = rollRad,
                        outEye = out.eye,
                        outCenter = out.center,
                        outUp = out.up,
                    )
                    CameraOptics.cameraBasis(out.eye, out.center, rollRad, scratchForward, scratchRight, scratchUp)
                    val ox = lerp(rig.startOffset.x, rig.endOffset.x, t)
                    val oy = lerp(rig.startOffset.y, rig.endOffset.y, t)
                    val oz = lerp(rig.startOffset.z, rig.endOffset.z, t)
                    out.eye.x += scratchRight.x * ox + scratchUp.x * oy - scratchForward.x * oz
                    out.eye.y += scratchRight.y * ox + scratchUp.y * oy - scratchForward.y * oz
                    out.eye.z += scratchRight.z * ox + scratchUp.z * oy - scratchForward.z * oz
                    CameraOptics.cameraBasis(out.eye, out.center, rollRad, scratchForward, scratchRight, out.up)
                }
            }

            is ShotRigSpec.Crane -> {
                val t = rig.easing.evaluate(progress)
                solveFraming(
                    spec = rig.framing,
                    stage = stage,
                    fovYRadians = fovY,
                    aspectRatio = aspectRatio,
                    rollRadians = rollRad,
                    azimuthDeltaDeg = lerp(rig.startAzimuthDeg, rig.endAzimuthDeg, t),
                    radiusMultiplier = lerp(rig.startRadiusMultiplier, rig.endRadiusMultiplier, t),
                    heightDelta = lerp(rig.startHeightDelta, rig.endHeightDelta, t),
                    outEye = out.eye,
                    outCenter = out.center,
                    outUp = out.up,
                )
            }

            is ShotRigSpec.SplineRail -> {
                val spline = shot.inlineSpline ?: rig.railName?.let(stage::readSpline)
                val t = rig.easing.evaluate(progress)
                val u = lerp(rig.startFraction, rig.endFraction, t)
                if (spline != null) {
                    spline.samplePosition(u, out.eye)
                    when (rig.lookMode) {
                        SplineLookMode.Subject -> {
                            if (framingSolver.resolveSubjects(rig.framing, stage, tempSubject)) {
                                out.center.set(
                                    (tempSubject.boundsMin.x + tempSubject.boundsMax.x) * HALF,
                                    tempSubject.boundsMin.y + tempSubject.height * rig.framing.shotSize.verticalAnchor,
                                    (tempSubject.boundsMin.z + tempSubject.boundsMax.z) * HALF,
                                )
                            } else {
                                rig.framing.fallbackPoint.copyTo(out.center)
                            }
                        }

                        SplineLookMode.ForwardTangent -> {
                            spline.samplePosition((u + rig.lookAheadFraction).coerceAtMost(1f), out.center)
                            if (out.eye.squaredDistanceTo(out.center) < EPSILON) {
                                spline.sampleTangent(u, scratchForward)
                                out.center.set(out.eye).add(scratchForward)
                            }
                        }

                        SplineLookMode.FixedPoint -> {
                            (rig.fixedLookPoint ?: rig.framing.fallbackPoint).copyTo(out.center)
                        }
                    }
                    if (rig.bankDegrees != 0f) {
                        rollRad += sin(PI_F * progress) * rig.bankDegrees * DEG_TO_RAD
                    }
                    CameraOptics.cameraBasis(out.eye, out.center, rollRad, scratchForward, scratchRight, out.up)
                } else {
                    solveFraming(
                        spec = rig.framing,
                        stage = stage,
                        fovYRadians = fovY,
                        aspectRatio = aspectRatio,
                        rollRadians = rollRad,
                        outEye = out.eye,
                        outCenter = out.center,
                        outUp = out.up,
                    )
                }
            }

            is ShotRigSpec.ChaseFollow -> {
                solveFraming(
                    spec = rig.framing,
                    stage = stage,
                    fovYRadians = fovY,
                    aspectRatio = aspectRatio,
                    rollRadians = rollRad,
                    outEye = out.eye,
                    outCenter = out.center,
                    outUp = out.up,
                )
            }

            is ShotRigSpec.DollyZoom -> {
                val t = rig.easing.evaluate(progress)
                val startFov = CameraOptics.focalLengthMmToFovYRadians(rig.startFocalLengthMm)
                val endFov = CameraOptics.focalLengthMmToFovYRadians(rig.endFocalLengthMm)
                fovY = lerp(startFov, endFov, t)
                val baseDistance = framingSolver.solve(
                    spec = rig.framing,
                    stage = stage,
                    fovYRadians = startFov,
                    aspectRatio = aspectRatio,
                    rollRadians = rollRad,
                    outEye = scratchVecA,
                    outCenter = out.center,
                    outUp = out.up,
                )
                val coupledDistance = CameraOptics.dollyZoomDistance(baseDistance, startFov, fovY)
                // Reposition eye along the exact radial vector from center at coupledDistance.
                scratchVecB.set(scratchVecA).sub(out.center).normalize().scale(coupledDistance)
                out.eye.set(out.center).add(scratchVecB)
                CameraOptics.cameraBasis(out.eye, out.center, rollRad, scratchForward, scratchRight, out.up)
            }

            is ShotRigSpec.WhipPan -> {
                val t = rig.easing.evaluate(progress)
                if (rig.toSubject != null && stage.readSubject(rig.toSubject, tempSubject2)) {
                    solveFraming(
                        spec = rig.framing,
                        stage = stage,
                        fovYRadians = fovY,
                        aspectRatio = aspectRatio,
                        rollRadians = rollRad,
                        outEye = out.eye,
                        outCenter = scratchVecA,
                        outUp = out.up,
                    )
                    tempSubject2.boundsCenter(scratchVecB)
                    out.center.set(scratchVecA).lerp(scratchVecB, t)
                    CameraOptics.cameraBasis(out.eye, out.center, rollRad, scratchForward, scratchRight, out.up)
                } else {
                    solveFraming(
                        spec = rig.framing,
                        stage = stage,
                        fovYRadians = fovY,
                        aspectRatio = aspectRatio,
                        rollRadians = rollRad,
                        azimuthDeltaDeg = lerp(rig.startAzimuthDeg, rig.endAzimuthDeg, t),
                        outEye = out.eye,
                        outCenter = out.center,
                        outUp = out.up,
                    )
                }
            }

            is ShotRigSpec.NamedCamera -> {
                if (!stage.readNamedCamera(rig.cameraName, out)) {
                    out.reset()
                }
                fovY = out.fovYRadians
                rollRad = out.rollRadians
            }
        }

        out.fovYRadians = fovY
        out.rollRadians = rollRad
    }

    @Suppress("LongParameterList")
    private fun solveFraming(
        spec: FramingSpec,
        stage: StageQuery,
        fovYRadians: Float,
        aspectRatio: Float,
        rollRadians: Float,
        azimuthDeltaDeg: Float = 0f,
        elevationDeltaDeg: Float = 0f,
        radiusMultiplier: Float = 1f,
        heightDelta: Float = 0f,
        outEye: Vec3f,
        outCenter: Vec3f,
        outUp: Vec3f,
    ) {
        framingSolver.solve(
            spec = spec,
            stage = stage,
            fovYRadians = fovYRadians,
            aspectRatio = aspectRatio,
            rollRadians = rollRadians,
            azimuthDeltaDeg = azimuthDeltaDeg,
            elevationDeltaDeg = elevationDeltaDeg,
            radiusMultiplier = radiusMultiplier,
            heightDelta = heightDelta,
            outEye = outEye,
            outCenter = outCenter,
            outUp = outUp,
        )
    }

    @Suppress("CyclomaticComplexMethod", "NestedBlockDepth", "LongMethod")
    private fun evaluateAtmosphere(
        shot: ResolvedShot,
        cameraPose: DirectedCameraPose,
        stage: StageQuery,
        out: DirectedAtmosphereState,
    ) {
        out.reset()

        // 1. Per-shot baseline mood overrides.
        shot.spec.atmosphere?.let { atmo ->
            atmo.dayTime?.let {
                out.hasDayTime = true
                out.dayTime = it.mod(1f)
            }
            if (atmo.sunColor != null || atmo.sunIntensity != null || atmo.sunAmbient != null) {
                out.hasDirectionalLight = true
                atmo.sunColor?.copyTo(out.sunColor)
                atmo.sunIntensity?.let { out.sunIntensity = it }
                atmo.sunAmbient?.let { out.sunAmbient = it }
            }
            if (atmo.fogEnabled != null || atmo.fogDensity != null || atmo.fogColor != null) {
                out.hasFog = true
                atmo.fogEnabled?.let { out.fogEnabled = it }
                atmo.fogDensity?.let { out.fogDensity = it }
                atmo.fogColor?.copyTo(out.fogColor)
            }
            if (atmo.skyHorizon != null || atmo.skyZenith != null || atmo.skyExposure != null) {
                out.hasSky = true
                atmo.skyHorizon?.copyTo(out.skyHorizon)
                atmo.skyZenith?.copyTo(out.skyZenith)
                atmo.skyExposure?.let { out.skyExposure = it }
            }
            val hasTone = atmo.exposure != null || atmo.contrast != null || atmo.saturation != null
            val hasBalance = atmo.whiteBalanceTemperature != null || atmo.whiteBalanceTint != null
            val hasBloom = atmo.bloomIntensity != null || atmo.bloomThreshold != null
            if (hasTone || hasBalance || hasBloom) {
                out.hasGrade = true
                atmo.exposure?.let { out.exposure = it }
                atmo.contrast?.let { out.contrast = it }
                atmo.saturation?.let { out.saturation = it }
                atmo.whiteBalanceTemperature?.let { out.whiteBalanceTemperature = it }
                atmo.whiteBalanceTint?.let { out.whiteBalanceTint = it }
                atmo.bloomIntensity?.let { out.bloomIntensity = it }
                atmo.bloomThreshold?.let { out.bloomThreshold = it }
            }
        }

        // 2. Parallel lighting choreography cues on lightingTrack.
        val now = currentTimeSeconds
        for (i in resolvedLighting.indices) {
            val item = resolvedLighting[i]
            if (now >= item.startTime) {
                val localT = ((now - item.startTime) / item.duration).coerceIn(0f, 1f)
                val activeWindow = now <= item.endTime

                when (val cue = item.spec) {
                    is LightingCueSpec.DayCycleSweep -> {
                        val eased = cue.easing.evaluate(localT)
                        out.hasDayTime = true
                        out.dayTime = lerp(cue.fromTime, cue.toTime, eased).mod(1f)
                    }

                    is LightingCueSpec.DirectionalLight -> {
                        val eased = cue.easing.evaluate(localT)
                        out.hasDirectionalLight = true
                        out.sunColor.set(
                            lerp(out.sunColor.x, cue.color.r, eased),
                            lerp(out.sunColor.y, cue.color.g, eased),
                            lerp(out.sunColor.z, cue.color.b, eased),
                        )
                        out.sunIntensity = lerp(out.sunIntensity, cue.intensity, eased)
                        cue.ambient?.let { out.sunAmbient = it }
                        if (cue.elevationDeg != null || cue.azimuthDeg != null) {
                            val elev = (cue.elevationDeg ?: DEFAULT_SUN_ELEV_DEG) * DEG_TO_RAD
                            val az = (cue.azimuthDeg ?: DEFAULT_SUN_AZ_DEG) * DEG_TO_RAD
                            val ce = cos(elev)
                            out.hasSunDirection = true
                            out.sunDirection.set(sin(az) * ce, sin(elev), -cos(az) * ce).normalize()
                        }
                    }

                    is LightingCueSpec.HeroRimLight -> {
                        if (activeWindow && stage.readSubject(cue.subject, tempSubject)) {
                            val light = out.acquireDynamicLight()
                            if (light != null) {
                                val elapsed = now - item.startTime
                                val remaining = item.endTime - now
                                val envIn = if (cue.fadeInSeconds > EPSILON) (elapsed / cue.fadeInSeconds).coerceIn(0f, 1f) else 1f
                                val envOut = if (cue.fadeOutSeconds > EPSILON) (remaining / cue.fadeOutSeconds).coerceIn(0f, 1f) else 1f
                                val envelope = minOf(envIn, envOut)

                                val toCamYaw = atan2(
                                    cameraPose.eye.x - tempSubject.position.x,
                                    -(cameraPose.eye.z - tempSubject.position.z),
                                )
                                val rimBearing = toCamYaw + cue.azimuthFromCameraDeg * DEG_TO_RAD
                                light.id = item.slotId
                                light.position.set(
                                    tempSubject.position.x + sin(rimBearing) * cue.distance,
                                    tempSubject.position.y + cue.heightOffset,
                                    tempSubject.position.z - cos(rimBearing) * cue.distance,
                                )
                                cue.color.copyTo(light.color)
                                light.intensity = cue.intensity * envelope
                                light.range = cue.range
                            }
                        }
                    }

                    is LightingCueSpec.LightFlash -> {
                        if (activeWindow) {
                            val light = out.acquireDynamicLight()
                            if (light != null) {
                                val peaks = cue.flickerPeaks.coerceAtLeast(1)
                                val wave = kotlin.math.abs(sin(localT * PI_F * peaks))
                                val decay = exp(-FLASH_DECAY_RATE * localT)
                                val strength = (wave * decay).coerceIn(0f, 1f)
                                light.id = item.slotId
                                if (cue.subject != null && stage.readSubject(cue.subject, tempSubject)) {
                                    light.position.set(
                                        tempSubject.position.x + cue.position.x,
                                        tempSubject.position.y + cue.position.y,
                                        tempSubject.position.z + cue.position.z,
                                    )
                                } else {
                                    cue.position.copyTo(light.position)
                                }
                                cue.color.copyTo(light.color)
                                light.intensity = cue.peakIntensity * strength
                                light.range = cue.range
                                if (cue.boostSunIntensity > 0f) {
                                    out.hasDirectionalLight = true
                                    out.sunIntensity += cue.boostSunIntensity * strength
                                }
                            }
                        }
                    }

                    is LightingCueSpec.FogRamp -> {
                        val eased = cue.easing.evaluate(localT)
                        out.hasFog = true
                        out.fogEnabled = cue.enabled
                        out.fogDensity = lerp(cue.fromDensity, cue.toDensity, eased)
                        out.fogColor.set(
                            lerp(cue.fromColor.r, cue.toColor.r, eased),
                            lerp(cue.fromColor.g, cue.toColor.g, eased),
                            lerp(cue.fromColor.b, cue.toColor.b, eased),
                        )
                    }

                    is LightingCueSpec.ColorGrade -> {
                        val eased = cue.easing.evaluate(localT)
                        out.hasGrade = true
                        out.exposure = lerp(out.exposure, cue.exposure, eased)
                        out.contrast = lerp(out.contrast, cue.contrast, eased)
                        out.saturation = lerp(out.saturation, cue.saturation, eased)
                        out.whiteBalanceTemperature = lerp(out.whiteBalanceTemperature, cue.whiteBalanceTemperature, eased)
                        out.whiteBalanceTint = lerp(out.whiteBalanceTint, cue.whiteBalanceTint, eased)
                        cue.bloomIntensity?.let { target ->
                            val start = if (out.bloomIntensity >= 0f) out.bloomIntensity else 0f
                            out.bloomIntensity = lerp(start, target, eased)
                        }
                        cue.bloomThreshold?.let { out.bloomThreshold = it }
                    }
                }
            }
        }
    }

    private fun dispatchAudioCues(sink: DirectorCueSink) {
        val now = currentTimeSeconds
        for (i in resolvedAudio.indices) {
            if (!audioCueFired[i] && now >= resolvedAudio[i].triggerTime) {
                audioCueFired[i] = true
                when (val cue = resolvedAudio[i].spec) {
                    is AudioCueSpec.PlaySfx -> sink.onPlaySfx(cue)
                    is AudioCueSpec.MusicControl -> sink.onMusicState(cue)
                }
            }
        }
    }

    private fun dispatchActionCues(sink: DirectorCueSink) {
        val now = currentTimeSeconds
        for (i in resolvedActions.indices) {
            val item = resolvedActions[i]
            if (!actionCueFired[i] && now >= item.startTime) {
                actionCueFired[i] = true
                when (val cue = item.spec) {
                    is ActionCueSpec.InputAction -> sink.onInputAction(
                        action = cue.action,
                        active = true,
                        durationSeconds = (item.endTime - item.startTime).coerceAtLeast(0f),
                    )
                    is ActionCueSpec.ActorMove -> sink.onActorCue(cue)
                    is ActionCueSpec.PlayAnimation -> sink.onAnimationCue(cue)
                    is ActionCueSpec.ParticleBurst -> sink.onParticleCue(cue)
                    is ActionCueSpec.CameraTrauma -> feelRig.addTrauma(cue.amount, cue.decaySeconds)
                    is ActionCueSpec.TimeScaleRamp -> Unit // Continuous evaluation in updateTimeScale().
                }
            }
            val pendingRelease = actionCueFired[i] && !inputActionReleased[i] && now >= item.endTime
            if (item.spec is ActionCueSpec.InputAction && pendingRelease) {
                inputActionReleased[i] = true
                sink.onInputAction(item.spec.action, active = false, durationSeconds = 0f)
            }
        }
    }

    private fun updateTimeScale() {
        var scale = 1f
        var pitchScale = 1f
        val now = currentTimeSeconds
        for (i in resolvedSpeedRamps.indices) {
            val ramp = resolvedSpeedRamps[i]
            val cue = ramp.spec
            val elapsed = now - ramp.startTime
            val hitstopEnd = cue.hitstopSeconds.coerceAtLeast(0f)
            val rampInEnd = hitstopEnd + cue.rampInSeconds.coerceAtLeast(0f)
            val holdEnd = rampInEnd + cue.holdSeconds.coerceAtLeast(0f)
            val rampOutEnd = holdEnd + cue.rampOutSeconds.coerceAtLeast(0f)
            if (elapsed in 0f..rampOutEnd) {
                val s = when {
                    elapsed < hitstopEnd -> 0f
                    elapsed < rampInEnd -> {
                        val t = if (cue.rampInSeconds > EPSILON) (elapsed - hitstopEnd) / cue.rampInSeconds else 1f
                        lerp(1f, cue.targetScale, EasingCurve.EaseOutCubic.evaluate(t))
                    }
                    elapsed < holdEnd -> cue.targetScale
                    else -> {
                        val t = if (cue.rampOutSeconds > EPSILON) (elapsed - holdEnd) / cue.rampOutSeconds else 1f
                        lerp(cue.targetScale, 1f, EasingCurve.EaseInOutCubic.evaluate(t))
                    }
                }
                scale *= s
                if (cue.pitchShiftAudio) {
                    pitchScale *= s.coerceAtLeast(MIN_AUDIO_PITCH_SCALE)
                }
            }
        }
        currentTimeScale = scale.coerceAtLeast(0f)
        currentAudioPitchScale = pitchScale.coerceAtLeast(MIN_AUDIO_PITCH_SCALE)
    }

    private fun findShotAt(time: Float): ResolvedShot {
        for (i in resolvedShots.indices) {
            if (time < resolvedShots[i].endTime || i == resolvedShots.lastIndex) {
                return resolvedShots[i]
            }
        }
        return resolvedShots.last()
    }

    private class ResolvedShot(
        val index: Int,
        val spec: ShotSpec,
        val startTime: Float,
        val endTime: Float,
        val duration: Float,
        val inlineSpline: ArcLengthSpline3f?,
        val collision: com.awakekt.awake.core.camera.CameraCollision?,
    )

    private class ResolvedLightingCue(
        val spec: LightingCueSpec,
        val startTime: Float,
        val endTime: Float,
        val duration: Float,
        val slotId: String,
    )

    private class ResolvedAudioCue(
        val spec: AudioCueSpec,
        val triggerTime: Float,
    )

    private class ResolvedActionCue(
        val spec: ActionCueSpec,
        val startTime: Float,
        val endTime: Float,
    )

    private class ResolvedSpeedRamp(
        val spec: ActionCueSpec.TimeScaleRamp,
        val startTime: Float,
    )

    private companion object {
        const val MIN_DURATION = 1e-4f
        const val MIN_ASPECT = 0.25f
        const val EPSILON = 1e-5f
        const val TIME_END_EPSILON = 1e-4
        const val HALF = 0.5f
        const val PI_F = PI.toFloat()
        const val DEG_TO_RAD = (PI / 180.0).toFloat()
        const val DEFAULT_SUN_ELEV_DEG = 45f
        const val DEFAULT_SUN_AZ_DEG = 135f
        const val FLASH_DECAY_RATE = 3.2f
        const val MIN_AUDIO_PITCH_SCALE = 0.35f
        val DEFAULT_STATIC_EYE = DirectorVec3(0f, 2f, 6f)
        val DEFAULT_FRAMING = FramingSpec()
    }
}

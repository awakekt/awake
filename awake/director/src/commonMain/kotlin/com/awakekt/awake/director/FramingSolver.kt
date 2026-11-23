/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.director

import com.awakekt.awake.core.camera.CameraOptics
import com.awakekt.awake.core.camera.CameraPose
import com.awakekt.awake.core.math.Vec3f
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Standard cinematic shot sizes expressing how large the subject fills the vertical viewport.
 *
 * Using shot sizes instead of raw camera distances lets humans and AI directors keep consistent
 * framing across any lens focal length or character height.
 *
 * @property verticalFillRatio Target fraction of viewport height occupied by the subject's height.
 * @property verticalAnchor Normalized height along the subject's AABB (`0` = feet, `0.5` = torso,
 *   `0.82` = head/eyes) where the lens aims.
 */
@Serializable
enum class ShotSize(
    val verticalFillRatio: Float,
    val verticalAnchor: Float,
) {
    /** Tight detail on eyes/face/emblem (fills ~82% vertically). */
    @SerialName("extreme_close_up")
    ExtremeCloseUp(verticalFillRatio = 0.82f, verticalAnchor = 0.85f),

    /** Head and shoulders portrait framing (fills ~65% vertically). */
    @SerialName("close_up")
    CloseUp(verticalFillRatio = 0.65f, verticalAnchor = 0.80f),

    /** Chest-up dramatic framing (fills ~52% vertically). */
    @SerialName("medium_close_up")
    MediumCloseUp(verticalFillRatio = 0.52f, verticalAnchor = 0.72f),

    /** Waist-up framing balancing expression and motion (fills ~42% vertically). */
    @SerialName("medium")
    Medium(verticalFillRatio = 0.42f, verticalAnchor = 0.62f),

    /** Mid-thigh / action hero framing (fills ~34% vertically). */
    @SerialName("cowboy")
    Cowboy(verticalFillRatio = 0.34f, verticalAnchor = 0.55f),

    /** Full-body framing with clean headroom and foot clearance (fills ~26% vertically). */
    @SerialName("full")
    Full(verticalFillRatio = 0.26f, verticalAnchor = 0.50f),

    /** Wide framing emphasizing environment and combat space (fills ~18% vertically). */
    @SerialName("wide")
    Wide(verticalFillRatio = 0.18f, verticalAnchor = 0.48f),

    /** Grand establishing vista framing the landmark/subject in context (fills ~11% vertically). */
    @SerialName("establishing")
    Establishing(verticalFillRatio = 0.11f, verticalAnchor = 0.45f),
}

/**
 * Semantic camera placement presets relative to the subject's facing direction.
 *
 * @property defaultAzimuthDeg Default horizontal bearing around the subject in degrees (`0` = in
 *   front of subject looking back at its face, `180` = directly behind).
 * @property defaultElevationDeg Default vertical camera elevation angle in degrees (`+` camera
 *   above looking down, `-` camera low looking up).
 * @property defaultScreenOffsetX Default horizontal screen composition shift (`-` places subject
 *   left of center, `+` places subject right of center).
 */
@Serializable
enum class AnglePreset(
    val defaultAzimuthDeg: Float,
    val defaultElevationDeg: Float,
    val defaultScreenOffsetX: Float = 0f,
) {
    /** Extreme low angle near the ground looking up for monumental scale. */
    @SerialName("worms_eye")
    WormsEye(defaultAzimuthDeg = 20f, defaultElevationDeg = -22f),

    /** Classic heroic low angle looking slightly upward at the subject. */
    @SerialName("low_hero")
    LowHero(defaultAzimuthDeg = 28f, defaultElevationDeg = -12f),

    /** Neutral eye-level cinematic angle. */
    @SerialName("eye_level")
    EyeLevel(defaultAzimuthDeg = 22f, defaultElevationDeg = 4f),

    /** Elevated tactical/cinematic angle looking down on the action. */
    @SerialName("high")
    High(defaultAzimuthDeg = 35f, defaultElevationDeg = 28f),

    /** Steep overhead bird's-eye angle. */
    @SerialName("birds_eye")
    BirdsEye(defaultAzimuthDeg = 0f, defaultElevationDeg = 68f),

    /** Over-the-left-shoulder framing looking forward past the subject. */
    @SerialName("over_shoulder_left")
    OverShoulderLeft(defaultAzimuthDeg = 160f, defaultElevationDeg = 8f, defaultScreenOffsetX = -0.18f),

    /** Over-the-right-shoulder framing looking forward past the subject. */
    @SerialName("over_shoulder_right")
    OverShoulderRight(defaultAzimuthDeg = -160f, defaultElevationDeg = 8f, defaultScreenOffsetX = 0.18f),

    /** Side-tracking profile from the subject's left side. */
    @SerialName("profile_left")
    ProfileLeft(defaultAzimuthDeg = -90f, defaultElevationDeg = 4f),

    /** Side-tracking profile from the subject's right side. */
    @SerialName("profile_right")
    ProfileRight(defaultAzimuthDeg = 90f, defaultElevationDeg = 4f),
}

/**
 * Standard horizontal screen composition rules.
 *
 * @property screenOffsetX Normalized horizontal target position offset from screen center
 *   (`-0.167f` places the subject on the left Rule-of-Thirds line; `+0.167f` on the right).
 */
@Serializable
enum class ScreenComposition(val screenOffsetX: Float) {
    /** Centered symmetrical framing. */
    @SerialName("center")
    Center(0f),

    /** Subject placed on the left vertical Rule-of-Thirds line (`u = 1/3`). */
    @SerialName("rule_of_thirds_left")
    RuleOfThirdsLeft(-ONE_SIXTH),

    /** Subject placed on the right vertical Rule-of-Thirds line (`u = 2/3`). */
    @SerialName("rule_of_thirds_right")
    RuleOfThirdsRight(ONE_SIXTH),
}

/**
 * Semantic framing specification attached to camera shots so directors describe *what* they want
 * framed rather than hand-computing world coordinates.
 *
 * @property subjects Ordered list of subject entity or stage-marker names to frame (1 for a solo
 *   shot, 2+ for a two-shot or battle group framing).
 * @property fallbackPoint World-space fallback anchor when no subject is specified or found.
 * @property shotSize Semantic distance/coverage preset.
 * @property angle Relative camera bearing/elevation preset.
 * @property azimuthDeg Optional explicit azimuth override in degrees (relative to subject forward
 *   when [relativeToSubjectFacing] is true, else world `-Z`).
 * @property elevationDeg Optional explicit elevation override in degrees (`+` above, `-` below).
 * @property distanceOverride Optional explicit world-unit distance overriding [shotSize] auto-solve.
 * @property relativeToSubjectFacing Whether azimuth rotates with the primary subject's facing vector.
 * @property composition Rule-of-Thirds / centered composition rule.
 * @property screenOffsetX Fine horizontal composition shift in `-0.45..0.45` added on top of [composition].
 * @property screenOffsetY Fine vertical composition shift / headroom adjustment in `-0.45..0.45`.
 * @property lookAheadSeconds Velocity lead time in seconds so moving subjects stay comfortably ahead
 *   of the frame edge.
 * @property targetOffset World-space offset added to the aim anchor point.
 */
@Serializable
data class FramingSpec(
    val subjects: List<String> = emptyList(),
    val fallbackPoint: DirectorVec3 = DirectorVec3(0f, 1f, 0f),
    val shotSize: ShotSize = ShotSize.Medium,
    val angle: AnglePreset = AnglePreset.EyeLevel,
    val azimuthDeg: Float? = null,
    val elevationDeg: Float? = null,
    val distanceOverride: Float? = null,
    val relativeToSubjectFacing: Boolean = true,
    val composition: ScreenComposition = ScreenComposition.Center,
    val screenOffsetX: Float = 0f,
    val screenOffsetY: Float = 0f,
    val lookAheadSeconds: Float = 0f,
    val targetOffset: DirectorVec3 = DirectorVec3.Zero,
)

/**
 * Zero-allocation solver converting a semantic [FramingSpec] and live [StageQuery] into exact
 * world-space camera `eye`, `center`, and `up` vectors.
 */
class FramingSolver {
    private val combinedSubject = StageSubjectState()
    private val extraSubject = StageSubjectState()
    private val anchor = Vec3f()
    private val forward = Vec3f()
    private val right = Vec3f()
    private val up = Vec3f()

    /**
     * Resolves the combined [StageSubjectState] for all subjects named in [spec], returning `true`
     * if at least one subject was located on stage.
     */
    fun resolveSubjects(spec: FramingSpec, stage: StageQuery, out: StageSubjectState): Boolean {
        out.reset()
        var foundCount = 0
        for (i in spec.subjects.indices) {
            val name = spec.subjects[i]
            if (foundCount == 0) {
                if (stage.readSubject(name, out)) {
                    foundCount++
                }
            } else {
                if (stage.readSubject(name, extraSubject)) {
                    out.expandToInclude(extraSubject)
                    foundCount++
                }
            }
        }
        return foundCount > 0
    }

    /**
     * Solves a semantic camera pose into [outEye], [outCenter], and [outUp] with optional dynamic
     * deltas (used by orbit, crane, dolly, and whip-pan rigs).
     *
     * @param spec Base semantic framing specification.
     * @param stage Stage spatial query provider.
     * @param fovYRadians Active vertical field of view in radians.
     * @param aspectRatio Viewport aspect ratio (`width / height`).
     * @param rollRadians Camera Dutch roll angle in radians.
     * @param azimuthDeltaDeg Additional azimuth sweep in degrees.
     * @param elevationDeltaDeg Additional elevation sweep in degrees.
     * @param radiusMultiplier Multiplier applied to the solved distance (`1f` = exact shot size).
     * @param heightDelta Additional vertical lift applied to eye (for crane moves).
     * @param outEye Destination eye position vector mutated in place.
     * @param outCenter Destination look-at center vector mutated in place.
     * @param outUp Destination rolled up vector mutated in place.
     * @return The resolved world-unit camera distance from anchor to eye.
     */
    @Suppress("LongParameterList", "LongMethod")
    fun solve(
        spec: FramingSpec,
        stage: StageQuery,
        fovYRadians: Float,
        aspectRatio: Float,
        rollRadians: Float = 0f,
        azimuthDeltaDeg: Float = 0f,
        elevationDeltaDeg: Float = 0f,
        radiusMultiplier: Float = 1f,
        heightDelta: Float = 0f,
        outEye: Vec3f,
        outCenter: Vec3f,
        outUp: Vec3f,
    ): Float {
        val hasSubject = resolveSubjects(spec, stage, combinedSubject)
        val subjectHeight: Float
        val subjectWidth: Float
        val baseFacingYaw: Float

        if (hasSubject) {
            subjectHeight = combinedSubject.height
            subjectWidth = combinedSubject.width
            anchor.set(
                (combinedSubject.boundsMin.x + combinedSubject.boundsMax.x) * HALF,
                combinedSubject.boundsMin.y + subjectHeight * spec.shotSize.verticalAnchor,
                (combinedSubject.boundsMin.z + combinedSubject.boundsMax.z) * HALF,
            )
            if (spec.lookAheadSeconds > 0f) {
                anchor.x += combinedSubject.velocity.x * spec.lookAheadSeconds
                anchor.y += combinedSubject.velocity.y * spec.lookAheadSeconds
                anchor.z += combinedSubject.velocity.z * spec.lookAheadSeconds
            }
            baseFacingYaw = if (spec.relativeToSubjectFacing) {
                // Convert subject forward into CameraPose yaw (yaw 0 faces -Z, +yaw turns toward +X).
                atan2(combinedSubject.forward.x, -combinedSubject.forward.z)
            } else {
                0f
            }
        } else {
            subjectHeight = DEFAULT_FALLBACK_HEIGHT
            subjectWidth = DEFAULT_FALLBACK_WIDTH
            anchor.set(spec.fallbackPoint.x, spec.fallbackPoint.y, spec.fallbackPoint.z)
            baseFacingYaw = 0f
        }
        anchor.x += spec.targetOffset.x
        anchor.y += spec.targetOffset.y
        anchor.z += spec.targetOffset.z

        // Auto-solve distance from subject bounds & vertical FOV so framing matches ShotSize on any lens.
        val halfFovTan = tan(fovYRadians.coerceIn(CameraOptics.MIN_FOV_Y_RADIANS, CameraOptics.MAX_FOV_Y_RADIANS) * HALF)
        val baseDistance = spec.distanceOverride ?: run {
            val safeAspect = aspectRatio.coerceAtLeast(MIN_ASPECT)
            val verticalDistance = (subjectHeight * HALF) / (spec.shotSize.verticalFillRatio * halfFovTan)
            val horizontalFill = (spec.shotSize.verticalFillRatio * HORIZONTAL_FIT_FACTOR).coerceAtMost(MAX_HORIZONTAL_FILL)
            val horizontalDistance = (subjectWidth * HALF) / (horizontalFill * halfFovTan * safeAspect)
            maxOf(verticalDistance, horizontalDistance).coerceAtLeast(MIN_CAMERA_DISTANCE)
        }
        val distance = (baseDistance * radiusMultiplier.coerceAtLeast(MIN_RADIUS_MULT)).coerceAtLeast(MIN_CAMERA_DISTANCE)

        // Bearing: azimuth 0 places camera in front of subject looking back at its face.
        val azimuthDeg = (spec.azimuthDeg ?: spec.angle.defaultAzimuthDeg) + azimuthDeltaDeg
        val elevationDeg = ((spec.elevationDeg ?: spec.angle.defaultElevationDeg) + elevationDeltaDeg)
            .coerceIn(-MAX_ELEVATION_DEG, MAX_ELEVATION_DEG)

        val bearingRad = baseFacingYaw + azimuthDeg * DEG_TO_RAD
        val elevRad = elevationDeg * DEG_TO_RAD
        val cosElev = cos(elevRad)

        // Unit direction pointing FROM anchor TO camera eye.
        // At bearing 0 (subject faces -Z), camera sits along -Z in front of subject looking toward +Z.
        val radialX = sin(bearingRad) * cosElev
        val radialY = sin(elevRad)
        val radialZ = -cos(bearingRad) * cosElev

        outEye.set(
            anchor.x + radialX * distance,
            anchor.y + radialY * distance + heightDelta,
            anchor.z + radialZ * distance,
        )
        outCenter.set(anchor)

        // Apply Rule-of-Thirds & headroom offset in screen plane so the subject lands cleanly on the target grid line.
        CameraOptics.cameraBasis(outEye, outCenter, rollRadians, forward, right, up)
        val totalOffsetX = (spec.composition.screenOffsetX + spec.angle.defaultScreenOffsetX + spec.screenOffsetX)
            .coerceIn(-MAX_SCREEN_OFFSET, MAX_SCREEN_OFFSET)
        val totalOffsetY = spec.screenOffsetY.coerceIn(-MAX_SCREEN_OFFSET, MAX_SCREEN_OFFSET)

        if (totalOffsetX != 0f || totalOffsetY != 0f) {
            val viewHeightAtSubject = 2f * distance * halfFovTan
            val viewWidthAtSubject = viewHeightAtSubject * aspectRatio.coerceAtLeast(MIN_ASPECT)
            // Shifting look-at center to +right pushes the subject to the LEFT side of the screen (negative offsetX).
            val shiftRight = -totalOffsetX * viewWidthAtSubject
            val shiftUp = -totalOffsetY * viewHeightAtSubject
            outCenter.x += right.x * shiftRight + up.x * shiftUp
            outCenter.y += right.y * shiftRight + up.y * shiftUp
            outCenter.z += right.z * shiftRight + up.z * shiftUp
        }

        // Recompute exact up orthogonal to final eye -> center vector.
        CameraOptics.cameraBasis(outEye, outCenter, rollRadians, forward, right, outUp)
        return distance
    }

    /** Computes camera eye using [CameraPose.orbitEye] convention for a given pivot, yaw, pitch, and distance. */
    fun solveOrbitFromPivot(
        pivot: Vec3f,
        yawRad: Float,
        pitchRad: Float,
        distance: Float,
        rollRad: Float,
        outEye: Vec3f,
        outCenter: Vec3f,
        outUp: Vec3f,
    ) {
        outCenter.set(pivot)
        CameraPose.orbitEye(pivot, yawRad, pitchRad, distance, outEye)
        CameraOptics.cameraBasis(outEye, outCenter, rollRad, forward, right, outUp)
    }

    private companion object {
        const val HALF = 0.5f
        const val DEG_TO_RAD = (PI / 180.0).toFloat()
        const val DEFAULT_FALLBACK_HEIGHT = 1.8f
        const val DEFAULT_FALLBACK_WIDTH = 1.0f
        const val MIN_ASPECT = 0.25f
        const val MIN_CAMERA_DISTANCE = 0.25f
        const val MIN_RADIUS_MULT = 0.1f
        const val HORIZONTAL_FIT_FACTOR = 1.25f
        const val MAX_HORIZONTAL_FILL = 0.78f
        const val MAX_ELEVATION_DEG = 84f
        const val MAX_SCREEN_OFFSET = 0.45f
    }
}

private const val ONE_SIXTH = 1f / 6f

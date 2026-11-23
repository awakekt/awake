/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.director

import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.projectToViewport
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Viewport edge crossed by a framed subject's projected bounding box.
 */
@Serializable
enum class ViewportEdge {
    /** Subject extends past the left viewport border (`u < 0`). */
    @SerialName("left")
    Left,

    /** Subject extends past the right viewport border (`u > 1`). */
    @SerialName("right")
    Right,

    /** Subject extends past the top viewport border (`v < 0`). */
    @SerialName("top")
    Top,

    /** Subject extends past the bottom viewport border (`v > 1`). */
    @SerialName("bottom")
    Bottom,
}

/**
 * Quantitative cinematography diagnostics returned alongside storyboard contact sheets and shot
 * previews so developers and AI directors can spot framing, occlusion, and clearance errors without
 * guesswork.
 *
 * @property shotId Identifier of the evaluated shot.
 * @property timeSeconds Timeline timestamp in seconds.
 * @property beat Musical beat position at [timeSeconds].
 * @property subjectName Primary subject evaluated, or `null` for scenery/explicit shots.
 * @property subjectVisible True when at least part of the subject projects inside the viewport.
 * @property coverageWidth Fraction of viewport width covered by the subject's projected AABB (`0..1`).
 * @property coverageHeight Fraction of viewport height covered by the subject's projected AABB (`0..1`).
 * @property screenCenterX Normalized horizontal center of the subject in viewport space (`0` left .. `1` right).
 * @property screenCenterY Normalized vertical center of the subject in viewport space (`0` top .. `1` bottom).
 * @property clippedEdges Viewport borders cropped by the subject's projected bounding box.
 * @property occlusionFraction Fraction of sightline probe rays blocked before reaching the subject (`0..1`).
 * @property occluderName Name of the primary blocking entity when [occlusionFraction] > 0.
 * @property groundClearance Height of the camera eye above terrain (`NaN` when no terrain exists underneath).
 * @property averageLuminance Optional mean perceived brightness (`0..1`) measured from rendered pixels.
 * @property warnings Actionable cinematography warnings generated from the metrics above.
 */
@Serializable
data class ShotFramingMetrics(
    val shotId: String,
    val timeSeconds: Float,
    val beat: Float,
    val subjectName: String? = null,
    val subjectVisible: Boolean = true,
    val coverageWidth: Float = 0f,
    val coverageHeight: Float = 0f,
    val screenCenterX: Float = 0.5f,
    val screenCenterY: Float = 0.5f,
    val clippedEdges: List<ViewportEdge> = emptyList(),
    val occlusionFraction: Float = 0f,
    val occluderName: String? = null,
    val groundClearance: Float? = null,
    val averageLuminance: Float? = null,
    val warnings: List<String> = emptyList(),
)

/**
 * Pure-math shot quality inspector projecting subject bounding boxes into normalized screen space,
 * probing multi-point sightline occlusion, and checking terrain clearance.
 */
object FramingAnalyzer {
    /**
     * Evaluates framing quality for [shotId] at [timeSeconds] / [beat] given [pose] and [stage].
     *
     * @param shotId Shot identifier attached to the returned report.
     * @param timeSeconds Timeline timestamp in seconds.
     * @param beat Beat coordinate at [timeSeconds].
     * @param pose Evaluated camera pose to inspect.
     * @param aspectRatio Viewport aspect ratio (`width / height`).
     * @param subjects Named subjects expected in frame (empty for pure scenery shots).
     * @param targetShotSize Optional intended [ShotSize] used to flag framing-size mismatches.
     * @param stage Stage spatial query provider.
     * @param averageLuminance Optional rendered frame mean luminance (`0..1`).
     * @return Complete [ShotFramingMetrics] report with actionable warnings.
     */
    @Suppress("LongParameterList", "CyclomaticComplexMethod", "LongMethod", "NestedBlockDepth", "ReturnCount")
    fun analyze(
        shotId: String,
        timeSeconds: Float,
        beat: Float,
        pose: DirectedCameraPose,
        aspectRatio: Float,
        subjects: List<String>,
        targetShotSize: ShotSize? = null,
        stage: StageQuery,
        averageLuminance: Float? = null,
    ): ShotFramingMetrics {
        val warnings = mutableListOf<String>()
        val ground = stage.groundHeight(pose.eye.x, pose.eye.z)
        val groundClearance = if (ground.isFinite()) pose.eye.y - ground else null
        if (groundClearance != null && groundClearance < MIN_GROUND_CLEARANCE) {
            warnings += "Camera eye is too close to or below terrain (clearance=${round2(groundClearance)}m)"
        }
        if (averageLuminance != null) {
            if (averageLuminance < TOO_DARK_LUMINANCE) {
                warnings += "Frame appears very dark (luminance=${round2(averageLuminance)}); consider boosting light intensity or rim light"
            } else if (averageLuminance > TOO_BRIGHT_LUMINANCE) {
                warnings += "Frame appears blown out (luminance=${round2(averageLuminance)}); consider lowering exposure or flash intensity"
            }
        }

        val primaryName = subjects.firstOrNull()
        if (subjects.isEmpty()) {
            return ShotFramingMetrics(
                shotId = shotId,
                timeSeconds = timeSeconds,
                beat = beat,
                groundClearance = groundClearance,
                averageLuminance = averageLuminance,
                warnings = warnings,
            )
        }

        val solver = FramingSolver()
        val subjectState = StageSubjectState()
        val found = solver.resolveSubjects(FramingSpec(subjects = subjects), stage, subjectState)
        if (!found) {
            warnings += "Framed subject '${subjects.joinToString()}' was not found on stage"
            return ShotFramingMetrics(
                shotId = shotId,
                timeSeconds = timeSeconds,
                beat = beat,
                subjectName = primaryName,
                subjectVisible = false,
                groundClearance = groundClearance,
                averageLuminance = averageLuminance,
                warnings = warnings,
            )
        }

        val lens = Lens(
            eye = Vec3f(pose.eye.x, pose.eye.y, pose.eye.z),
            center = Vec3f(pose.center.x, pose.center.y, pose.center.z),
            up = Vec3f(pose.up.x, pose.up.y, pose.up.z),
            fovYRadians = pose.fovYRadians,
            near = pose.near,
            far = pose.far,
        )
        // Use a canonical 1x1 normalized viewport under WebGPU (+Y up, 0..1 depth) clip convention.
        val clipSpace = ClipSpace.WebGpu
        val vp = lens.viewProjectionMatrix(aspectRatio.coerceAtLeast(MIN_ASPECT), clipSpace)

        val min = subjectState.boundsMin
        val max = subjectState.boundsMax
        var minU = Float.POSITIVE_INFINITY
        var maxU = Float.NEGATIVE_INFINITY
        var minV = Float.POSITIVE_INFINITY
        var maxV = Float.NEGATIVE_INFINITY
        var projectedCorners = 0

        for (xi in 0..1) {
            val wx = if (xi == 0) min.x else max.x
            for (yi in 0..1) {
                val wy = if (yi == 0) min.y else max.y
                for (zi in 0..1) {
                    val wz = if (zi == 0) min.z else max.z
                    val p = lens.projectToViewport(Vec3f(wx, wy, wz), vp, 1f, 1f, clipSpace)
                    if (p != null) {
                        projectedCorners++
                        if (p.x < minU) minU = p.x
                        if (p.x > maxU) maxU = p.x
                        if (p.y < minV) minV = p.y
                        if (p.y > maxV) maxV = p.y
                    }
                }
            }
        }

        val visible = projectedCorners > 0 && maxU > 0f && minU < 1f && maxV > 0f && minV < 1f
        val clipped = buildList {
            if (visible) {
                if (minU < 0f) add(ViewportEdge.Left)
                if (maxU > 1f) add(ViewportEdge.Right)
                if (minV < 0f) add(ViewportEdge.Top)
                if (maxV > 1f) add(ViewportEdge.Bottom)
            }
        }
        val covW = if (visible) (maxU.coerceIn(0f, 1f) - minU.coerceIn(0f, 1f)).coerceAtLeast(0f) else 0f
        val covH = if (visible) (maxV.coerceIn(0f, 1f) - minV.coerceIn(0f, 1f)).coerceAtLeast(0f) else 0f
        val centerU = if (visible) ((minU + maxU) * HALF).coerceIn(-1f, 2f) else 0.5f
        val centerV = if (visible) ((minV + maxV) * HALF).coerceIn(-1f, 2f) else 0.5f

        if (!visible) {
            warnings += "Subject '$primaryName' is outside the camera frustum"
        } else {
            if (clipped.isNotEmpty() && targetShotSize != ShotSize.ExtremeCloseUp && targetShotSize != ShotSize.CloseUp) {
                warnings += "Subject '$primaryName' clipped on edge(s): ${clipped.joinToString { it.name.lowercase() }}"
            }
            if (targetShotSize != null) {
                val expected = targetShotSize.verticalFillRatio
                if (covH < expected * UNDERSIZE_RATIO) {
                    warnings += "Subject '$primaryName' fills ${percent(covH)}% height (expected ~${percent(expected)}% for ${targetShotSize.name})"
                } else if (covH > expected * OVERSIZE_RATIO && covH > MAX_CROWDED_COVERAGE) {
                    warnings += "Subject '$primaryName' fills ${percent(covH)}% height (crowded for ${targetShotSize.name})"
                }
            }
        }

        // Probe 5 sightline rays across subject bounds (center, head, feet, left, right).
        val cx = (min.x + max.x) * HALF
        val cy = (min.y + max.y) * HALF
        val cz = (min.z + max.z) * HALF
        val probes = arrayOf(
            Vec3f(cx, cy, cz),
            Vec3f(cx, min.y + subjectState.height * HEAD_FRACTION, cz),
            Vec3f(cx, min.y + subjectState.height * FEET_FRACTION, cz),
            Vec3f(min.x + (max.x - min.x) * QUARTER, cy, cz),
            Vec3f(min.x + (max.x - min.x) * THREE_QUARTERS, cy, cz),
        )
        val hit = StageRayHit()
        var blockedCount = 0
        var primaryOccluder: String? = null
        for (probe in probes) {
            if (stage.castSightline(pose.eye, probe, primaryName, hit) && hit.occluded) {
                blockedCount++
                if (primaryOccluder == null && hit.occluderName != null) {
                    primaryOccluder = hit.occluderName
                }
            }
        }
        val occlusion = blockedCount.toFloat() / probes.size
        if (occlusion >= OCCLUSION_WARN_THRESHOLD) {
            val by = primaryOccluder?.let { " by '$it'" }.orEmpty()
            warnings += "Subject '$primaryName' sightline is ${percent(occlusion)}% occluded$by"
        }

        return ShotFramingMetrics(
            shotId = shotId,
            timeSeconds = timeSeconds,
            beat = beat,
            subjectName = primaryName,
            subjectVisible = visible,
            coverageWidth = covW,
            coverageHeight = covH,
            screenCenterX = centerU,
            screenCenterY = centerV,
            clippedEdges = clipped,
            occlusionFraction = occlusion,
            occluderName = primaryOccluder,
            groundClearance = groundClearance,
            averageLuminance = averageLuminance,
            warnings = warnings,
        )
    }

    private fun percent(value: Float): Int = (value * HUNDRED + HALF).toInt()

    private fun round2(value: Float): Float = ((value * HUNDRED).toInt()) / HUNDRED

    private const val HALF = 0.5f
    private const val QUARTER = 0.25f
    private const val THREE_QUARTERS = 0.75f
    private const val HUNDRED = 100f
    private const val MIN_ASPECT = 0.25f
    private const val MIN_GROUND_CLEARANCE = 0.1f
    private const val TOO_DARK_LUMINANCE = 0.04f
    private const val TOO_BRIGHT_LUMINANCE = 0.94f
    private const val UNDERSIZE_RATIO = 0.45f
    private const val OVERSIZE_RATIO = 1.85f
    private const val MAX_CROWDED_COVERAGE = 0.85f
    private const val HEAD_FRACTION = 0.85f
    private const val FEET_FRACTION = 0.15f
    private const val OCCLUSION_WARN_THRESHOLD = 0.4f
}

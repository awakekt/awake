/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.command.GpuEnvironmentState
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.command.MASK_TARGET_LAYER_COUNT
import com.awakekt.awake.render.passes.maskExtentHeight
import com.awakekt.awake.render.passes.maskExtentWidth
import com.awakekt.awake.vulkan.pipeline.PipelineTable
import com.awakekt.awake.vulkan.pipeline.RenderPipeline
import com.awakekt.awake.vulkan.texture.DepthTarget

/**
 * Settles the scene-depth and mask targets before a presented frame's [input] is recorded (#1709):
 * each target stays at a dormant 1x1 stand-in until a frame actually needs it, grows immediately
 * to the rectangular scene extent (`surfaceWidth x surfaceHeight`) on demand so fog's
 * mask-vs-scene-depth texel grids stay aligned, and releases back to 1x1 once a residency window
 * passes with no active consumer.
 */
internal fun Renderer.settleMaskTarget(input: GpuPassInput) {
    val scenePass = sceneDepthPassFeature
    val mask = maskPass
    if (scenePass == null && mask == null) return
    val scenePlan = scenePass?.let { pass ->
        val needed = if (needsSceneDepth(input.environment)) 1 else 0
        val resident = if (pass.depthTarget.resident) 1 else 0
        val hold = sceneDepthResidency.frame(needed, resident)
        planTargetResize(
            target = pass.depthTarget,
            hold = hold,
            extent = if (hold > 0) {
                sceneDepthSizing.presentedExtent(pass.depthTarget.width, pass.depthTarget.height, surfaceWidth, surfaceHeight)
            } else {
                0L
            },
        )
    } ?: UNCHANGED_PLAN
    val maskPlan = mask?.let { pass ->
        val needed = if (hasEffectiveMasks(input.maskPasses, input.environment)) 1 else 0
        val resident = if (pass.depthTarget.resident) 1 else 0
        val hold = maskResidency.frame(needed, resident)
        planTargetResize(
            target = pass.depthTarget,
            hold = hold,
            extent = if (hold > 0) {
                maskSizing.presentedExtent(pass.depthTarget.width, pass.depthTarget.height, surfaceWidth, surfaceHeight)
            } else {
                0L
            },
        )
    } ?: UNCHANGED_PLAN
    applyScreenDepthPlans(scenePlan, maskPlan)
}

/**
 * Grows the scene-depth and/or mask targets for an offscreen render of [input] at
 * [width] x [height] pixels when needed, never shrinking either target.
 */
internal fun Renderer.ensureMaskTarget(width: Int, height: Int, input: GpuPassInput) {
    val scenePass = sceneDepthPassFeature
    val mask = maskPass
    if (scenePass == null && mask == null) return
    val scenePlan = scenePass?.let { pass ->
        val needed = if (needsSceneDepth(input.environment)) 1 else 0
        val resident = if (pass.depthTarget.resident) 1 else 0
        val hold = sceneDepthResidency.layersFor(needed, resident)
        planTargetResize(
            target = pass.depthTarget,
            hold = hold,
            extent = if (hold > 0) {
                sceneDepthSizing.offscreenExtent(pass.depthTarget.width, pass.depthTarget.height, width, height)
            } else {
                0L
            },
        )
    } ?: UNCHANGED_PLAN
    val maskPlan = mask?.let { pass ->
        val needed = if (hasEffectiveMasks(input.maskPasses, input.environment)) 1 else 0
        val resident = if (pass.depthTarget.resident) 1 else 0
        val hold = maskResidency.layersFor(needed, resident)
        planTargetResize(
            target = pass.depthTarget,
            hold = hold,
            extent = if (hold > 0) {
                maskSizing.offscreenExtent(pass.depthTarget.width, pass.depthTarget.height, width, height)
            } else {
                0L
            },
        )
    } ?: UNCHANGED_PLAN
    applyScreenDepthPlans(scenePlan, maskPlan)
}

private const val UNCHANGED_PLAN = -1L
private const val RELEASE_PLAN = 0L

private fun planTargetResize(target: DepthTarget, hold: Int, extent: Long): Long = if (hold == 0) {
    if (target.resident) RELEASE_PLAN else UNCHANGED_PLAN
} else {
    val width = maskExtentWidth(extent)
    val height = maskExtentHeight(extent)
    if (!target.resident || target.width != width || target.height != height) extent else UNCHANGED_PLAN
}

/**
 * Reallocates the scene-depth and/or mask targets once after a single [Renderer.waitIdle]: frames
 * in flight may read the old images, and their descriptor sets are rewritten in place.
 */
private fun Renderer.applyScreenDepthPlans(scenePlan: Long, maskPlan: Long) {
    if (scenePlan == UNCHANGED_PLAN && maskPlan == UNCHANGED_PLAN) return
    waitIdle()
    val scenePass = sceneDepthPassFeature
    if (scenePass != null && scenePlan != UNCHANGED_PLAN) {
        if (scenePlan == RELEASE_PLAN) {
            scenePass.resize(0, DepthTarget.DORMANT_SIZE, dormantLayers = 1)
        } else {
            scenePass.resize(1, maskExtentWidth(scenePlan), maskExtentHeight(scenePlan))
        }
    }
    val mask = maskPass
    if (mask != null && maskPlan != UNCHANGED_PLAN) {
        if (maskPlan == RELEASE_PLAN) {
            mask.resize(0, DepthTarget.DORMANT_SIZE, dormantLayers = MASK_TARGET_LAYER_COUNT)
        } else {
            mask.resize(MASK_TARGET_LAYER_COUNT, maskExtentWidth(maskPlan), maskExtentHeight(maskPlan))
        }
    }
}

internal fun Renderer.needsSceneDepth(environment: GpuEnvironmentState?): Boolean {
    var needed = anyScenePipelineSamplesSceneDepth
    val env = environment ?: GpuEnvironmentState.Default
    var i = 0
    while (!needed && i < renderFeatures.size) {
        needed = renderFeatures[i].needsSceneDepth(env)
        i += 1
    }
    return needed
}

internal fun PipelineTable.anyDeclaresSceneDepth(): Boolean {
    val maps = listOf(
        byFormat,
        backCulledByFormat,
        transparentByFormat,
        additiveByFormat,
        instancedByFormat,
        instancedBackCulledByFormat,
        skinnedInstancedByFormat,
        particlePipelines,
        additiveParticlePipelines,
        solidByFormat,
        solidBackCulledByFormat,
        solidInstancedByFormat,
        solidInstancedBackCulledByFormat,
        solidSkinnedInstancedByFormat,
    )
    return primary.declaresSceneDepthGroup ||
        maps.any(::anyPipelineDeclaresDepth) ||
        blendedRasterByFormat.values.any { byVariant -> byVariant.values.any { it.declaresSceneDepthGroup } }
}

private fun anyPipelineDeclaresDepth(map: Map<VertexFormat, RenderPipeline>): Boolean =
    map.values.any { it.declaresSceneDepthGroup }

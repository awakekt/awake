/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.Vec4
import com.awakekt.awake.core.math.times
import com.awakekt.awake.core.math.transformPosition
import com.awakekt.awake.render.command.GpuDebugView
import com.awakekt.awake.render.command.GpuDrawPreparationContext
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuDrawRequest
import com.awakekt.awake.render.command.GpuResolvedDraw
import com.awakekt.awake.render.passes.uniforms.DrawUniformPlan
import com.awakekt.awake.render.passes.uniforms.WIREFRAME_EDGE_CODE
import com.awakekt.awake.render.passes.uniforms.drawUniformPlan

/**
 * How far toward the eye a wireframe overlay draws each point, as a share of its distance: enough
 * for an edge to win the depth test against the faces it borders on every backend, too little to
 * show an edge another face hides.
 */
internal const val EDGE_PULL = 0.001f

/**
 * [viewProjection] with every point brought [EDGE_PULL] of its distance nearer, on the pixel it
 * already lands on, so the margin holds at any range. In perspective each point is pulled toward
 * [eye] first: a point and its pulled self lie on one ray from the eye. An orthographic view's
 * rays are parallel, so only its depth, linear there, is pulled toward the eye's.
 */
internal fun edgeViewProjection(viewProjection: Mat4, eye: Vec3f): Mat4 = if (viewProjection.isAffine()) {
    val eyeDepth = viewProjection.transformPosition(Vec4(eye.x, eye.y, eye.z, 1f)).z
    viewProjection * Mat4().apply {
        identity()
        m22 = 1f - EDGE_PULL
        m23 = eyeDepth * EDGE_PULL
    }
} else {
    Mat4().setTranslationScale(eye.x * EDGE_PULL, eye.y * EDGE_PULL, eye.z * EDGE_PULL, 1f - EDGE_PULL) * viewProjection
}

/** Whether this projection leaves `w` at 1, as an orthographic one does. */
private fun Mat4.isAffine(): Boolean = m30 == 0f && m31 == 0f && m32 == 0f

/**
 * The edges of [requests]' opaque scene draws, prepared after the draws themselves with [context]
 * made an edge context. They are numbered after every request, so a preparer's per-batch slots never
 * restart and an edge's uniforms never overwrite its fill's.
 */
internal fun GpuDrawPreparer.prepareEdges(requests: List<GpuDrawRequest>, context: GpuDrawPreparationContext): List<GpuResolvedDraw> {
    val edges = edgeContext(context)
    return requests.mapIndexedNotNull { index, request ->
        if (request.transparent || request.shadowsOnly) null else prepare(request, requests.size + index, edges)
    }
}

/** [context] for a wireframe overlay: the pulled view-projection, the edge code, and edges on. */
internal fun edgeContext(context: GpuDrawPreparationContext): GpuDrawPreparationContext = context.copy(
    viewProjection = edgeViewProjection(context.viewProjection, context.cameraEye),
    environment = context.environment.copy(debugView = GpuDebugView(code = WIREFRAME_EDGE_CODE)),
    edges = true,
)

/**
 * Whether a wireframe overlay draws this draw's edges: an opaque, single draw whose shader routes
 * its output through the debug views, so the edge code draws it in the edge grey. A backend's
 * preparer asks this when it prepares with an edges context, with its material's uniform size.
 */
fun GpuDrawRequest.drawsEdges(materialUniformFloatCount: Int): Boolean =
    instanceModels == null && !transparent && !shadowsOnly &&
        drawUniformPlan(mesh.format, materialUniformFloatCount, hasShadowCascades = false) in EDGE_PLANS

private val EDGE_PLANS = setOf(DrawUniformPlan.LitShadow, DrawUniformPlan.Skinned, DrawUniformPlan.TexturedPbr)

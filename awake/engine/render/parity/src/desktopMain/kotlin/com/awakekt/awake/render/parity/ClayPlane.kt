/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.RenderDebugView
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.SkinnedUniformLayout
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.render.texture.TextureAsset
import kotlinx.coroutines.runBlocking

/** How [renderClayPlaneScene] draws its plane: the same quad through each kind of lit shader. */
enum class ClayPlane {
    /** `textured`, with an orange texture. */
    TexturedOrange,

    /** `textured`, with a white texture. */
    TexturedWhite,

    /** `lit_shadow`, from white vertex colours and no texture. */
    Untextured,

    /** `skinned_textured`, bound to one joint at rest, with an orange texture. */
    Skinned,
}

/**
 * A plane facing up, drawn as [kind] under [view], lit by one slanted white sun with no shadows:
 * under [RenderDebugView.Clay] every kind draws the same grey, and lit they differ.
 *
 * @param kind Which shader draws the plane.
 * @param view What the shaders draw instead of their lit colour.
 * @param size The frame's width and height, in pixels.
 * @param wireframe Whether a wireframe overlay draws the plane's triangle edges over it.
 * @param jointOffset How far along x a [ClayPlane.Skinned] plane's joint moves it from rest.
 */
fun Renderer.renderClayPlaneScene(
    kind: ClayPlane,
    view: RenderDebugView,
    size: Int = SCENE_SIZE,
    wireframe: Boolean = false,
    jointOffset: Float = 0f,
): ByteArray {
    val target = createRenderTarget(size, size)
    val geometry = when (kind) {
        ClayPlane.TexturedOrange, ClayPlane.TexturedWhite -> texturedPlane()
        ClayPlane.Untextured -> plane(GROUND_HALF, y = 0f)
        ClayPlane.Skinned -> skinnedTexturedPlane()
    }
    val mesh = createMesh(geometry)
    val material = when (kind) {
        ClayPlane.TexturedOrange -> texturedMaterial(SolidOrange)
        ClayPlane.TexturedWhite -> texturedMaterial(SolidWhite)
        ClayPlane.Untextured -> createMaterial(LitShadowUniformLayout)
        ClayPlane.Skinned -> createMaterial(SkinnedUniformLayout, texture = SolidOrange)
    }
    val extras = when (kind) {
        ClayPlane.TexturedOrange, ClayPlane.TexturedWhite -> WHITE_FACTORS
        ClayPlane.Untextured -> FloatArray(0)
        ClayPlane.Skinned -> Mat4().setTranslationScale(jointOffset, 0f, 0f, 1f).data
    }
    return try {
        renderOnce(target, RenderDrawCommand(mesh, material, extraUniformFloats = extras), view, wireframe = wireframe)
    } finally {
        mesh.destroy()
        material.destroy()
        target.destroy()
    }
}

/**
 * A plane whose left half is bound wholly to joint 0 and whose right half is bound wholly to joint
 * 1, both at rest, with separate vertices along the seam, as a rigidly weighted rig has: drawn under
 * [view], picking joint [joint] for [RenderDebugView.SelectedJointWeight].
 */
fun Renderer.renderTwoJointPlaneScene(view: RenderDebugView, joint: Int = 0, texture: TextureAsset = SolidWhite, size: Int = SCENE_SIZE): ByteArray {
    val target = createRenderTarget(size, size)
    val mesh = createMesh(twoJointPlane())
    val material = createMaterial(SkinnedUniformLayout, texture = texture)
    return try {
        renderOnce(target, RenderDrawCommand(mesh, material, extraUniformFloats = Mat4().data + Mat4().data), view, joint)
    } finally {
        mesh.destroy()
        material.destroy()
        target.destroy()
    }
}

/** The camera every plane scene here is drawn from: above and in front of the plane, looking at its centre. */
internal fun clayPlaneLens() = Lens(eye = Vec3f(0f, EYE_Y, EYE_Z), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 50f)

private fun Renderer.renderOnce(
    target: RenderTarget,
    draw: RenderDrawCommand,
    view: RenderDebugView,
    joint: Int = 0,
    wireframe: Boolean = false,
): ByteArray {
    val lens = clayPlaneLens()
    renderToTexture(
        target,
        ScenePassCompiler.compile(
            lens = lens,
            drawCalls = listOf(draw),
            light = SceneLight(direction = CLAY_SUN, color = Vec3f(1f, 1f, 1f)),
            environment = EnvironmentUniforms.Default.copy(shadowsEnabled = false, debugView = view, debugLayer = joint, wireframe = wireframe),
            clipSpace = clipSpace,
            aspect = 1f,
            drawPreparer = (this as? GpuDrawPreparationSource)?.gpuDrawPreparer,
        ),
    )
    return runBlocking { readPixels(target) }.data
}

/** Two quads side by side, left on joint 0 and right on joint 1; joint indices are raw `uint32` bits. */
private fun twoJointPlane(half: Float = GROUND_HALF): MeshGeometry {
    fun vertex(x: Float, z: Float, joint: Int) = floatArrayOf(
        x, 0f, z, 0f, 1f, 0f, 1f, 1f, 1f, (x + half) / (2f * half), (z + half) / (2f * half),
        Float.fromBits(joint), 0f, 0f, 0f, 1f, 0f, 0f, 0f,
    )
    fun quad(left: Float, right: Float, joint: Int) =
        vertex(left, -half, joint) + vertex(right, -half, joint) + vertex(right, half, joint) + vertex(left, half, joint)
    return MeshGeometry(
        quad(-half, 0f, joint = 0) + quad(0f, half, joint = 1),
        intArrayOf(0, 1, 2, 2, 3, 0, 4, 5, 6, 6, 7, 4),
        VertexFormat.PositionNormalColorUvSkin,
    )
}

/** Slanted, so clay's cosine is neither 0 nor 1, and the same for every kind of plane. */
private val CLAY_SUN = Vec3f(0.5f, 1f, 0.3f)

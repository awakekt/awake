/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.debug

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.render.passes.uniforms.RenderDebugView
import com.awakekt.awake.scene.rendering.mesh.PbrMaterial

/** Singleton toggle set for [com.awakekt.awake.scene.rendering.systems
 * .DebugVisualizationSystem] -- add at most one to a `World` (an editor/debug entity), same
 * "system reads whatever's currently set" shape [PbrMaterial]'s `var` fields already use, so a
 * UI panel (a checkbox per field) can drive these live. All `false` by default: adding this
 * component with no changes draws nothing extra. */
data class WorldDebugSettings(
    /** Whether to draw the camera frustum lines for [frustumTargetEntityId]. */
    var showFrustum: Boolean = false,
    /** Whether to draw bounding boxes for scene entities. */
    var showBounds: Boolean = false,
    /** Draws one bounds box per submitted static instance, using its actual instance transform. */
    var showInstanceBounds: Boolean = false,
    /** Whether to draw occlusion culling debug volumes. */
    var showOcclusion: Boolean = false,
    /** Whether to draw visual wireframe indicators for scene light sources. */
    var showLights: Boolean = false,
    /** Whether to draw shadow projection frustum volumes. */
    var showShadowFrustum: Boolean = false,
    /** Whether to draw the ground plane reference grid. */
    var showGrid: Boolean = false,
    /** Whether to draw orientation axis indicator lines (X, Y, Z). */
    var showAxisLines: Boolean = false,
    /** Scale multiplier applied to the ground debug grid cell spacing. */
    var gridScale: Float = 1.0f,
    /** Distance at which ground debug grid lines smoothly fade out. */
    var gridFadeDistance: Float = 100.0f,
    /**
     * Whether the sun's shadow is fitted in cascades, or as the one fixed box that predates them.
     *
     * Off is not an optimisation -- `directionalShadowBox` covers a fixed volume AT THE ORIGIN,
     * so anything that walks away from it loses its shadow entirely. It is here to be switched
     * mid-frame against the cascaded fit, which is the only way to see what cascades are doing
     * to a scene rather than reading that they are on.
     */
    var cascadedShadows: Boolean = true,
    /**
     * A debugger-only shadow override. `null` preserves the authored directional-light setting;
     * `false` removes shadow-map work and sampling for the current frame.
     */
    var shadowsEnabledOverride: Boolean? = null,
    /** Debugger-only fog density override. `null` preserves the authored scene fog density. */
    var fogDensityOverride: Float? = null,
    /** Debugger-only fog color override. `null` preserves the authored scene fog color. */
    var fogColorOverride: Color? = null,
    /** Which entity's [com.awakekt.awake.scene.rendering.Camera]
     * [showFrustum] draws -- deliberately NOT the world's own primary/viewing camera: drawing
     * that camera's frustum from inside its own view is geometrically invisible (the near
     * plane sits behind the eye, the far/side planes align with the screen edges). An editor
     * sets this to whatever entity is selected each frame; `null` (or an entity with no
     * `Camera`) draws nothing, same "opt-in, no target = no lines" posture every other field
     * here already has. */
    var frustumTargetEntityId: Int? = null,
    /** What the scene shaders draw instead of their lit colour. */
    var renderDebugView: RenderDebugView = RenderDebugView.Off,
    /**
     * What a view that shows one of many picks: the shadow-map layer [RenderDebugView.ShadowMap]
     * shows (a cascade, or a point light's face), or the joint [RenderDebugView.SelectedJointWeight]
     * shows (an index into the skin's joints).
     */
    var renderDebugLayer: Int = 0,
    /**
     * Whether each opaque mesh's triangle edges are drawn over the frame, over whichever
     * [renderDebugView] is on. A skinned mesh's edges move with its pose. Instanced meshes, and
     * meshes whose shader shows no debug views, draw no edges.
     */
    var showWireframe: Boolean = false,
    /**
     * Whether each animated skin's bones are drawn as lines over the scene, in their current pose: one
     * from every joint to the joint above it, in the colour [RenderDebugView.JointWeights] gives that
     * joint. Shows whether a character's rig sits inside its mesh and moves with it.
     */
    var showSkeleton: Boolean = false,
)

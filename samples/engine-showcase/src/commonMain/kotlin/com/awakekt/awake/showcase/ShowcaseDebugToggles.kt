/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase

import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.scene.physics.physicsDebugLines
import com.awakekt.awake.scene.rendering.debug.debugSettings
import com.awakekt.awake.showcase.terrain.heightfieldDiagnosticLines

/**
 * What the showcase draws on top of the scene, for whoever is watching it.
 *
 * Off by default. The navigation grid is a diagnostic — a marker on every sample an agent cannot
 * stand on — and it reads as part of the demonstration rather than as an overlay when it is always
 * on, which is exactly the confusion it caused.
 *
 * An object, like the example drivers themselves: the drivers are singletons reached from a global
 * showcase list, so a per-module instance would have nowhere to be handed to them from.
 */
internal object ShowcaseDebugToggles {
    /** Draws unwalkable navigation samples and each chaser's current route. */
    var showNavGrid: Boolean = false

    /**
     * Draws the cells a long-range route passes through, including ones that are not loaded.
     *
     * Separate from [showNavGrid] because they show different layers: that one is what the fine
     * grid can see right now, this one is what the coarse graph remembers of everywhere else.
     */
    var showCorridor: Boolean = false

    /** Every mesh's bounding box -- what frustum culling and the spatial index actually test. */
    var showBounds: Boolean = false

    /** Every static-instancing transform, as a cyan wireframe box. */
    var showInstanceBounds: Boolean = false

    /** The boxes each shadow cascade renders from, one colour per cascade. */
    var showShadowCascades: Boolean = false

    /**
     * Whether the sun casts a shadow at all.
     *
     * With [cascadedShadows] this gives the three cases worth comparing: no shadow, the single
     * fixed box, and the cascaded fit. Reading a screenshot of one of them tells you very little;
     * switching between them tells you what each is doing.
     */
    var shadows: Boolean = true

    /**
     * Whether the sun is shadowed in cascades at all, as opposed to the one fixed box.
     *
     * Turning it off is how you SEE what cascades do: the fixed box covers a fixed volume at the
     * world origin, so distant casters simply stop having shadows. The toggle above only draws
     * the cascades' outlines, which shows where they are and not what they buy.
     */
    var cascadedShadows: Boolean = true

    /** Which boxes are occluders. Not per-entity occluded state; see `WorldDebugSettings`. */
    var showOcclusion: Boolean = false

    /** The scene light's direction and position gizmo. */
    var showLights: Boolean = false

    /**
     * Draws every collider as a wireframe, coloured by motion type.
     *
     * The collider and the mesh drawn for it are two different things, and the bugs that matter
     * live in the gap: a capsule sunk into terrain, a heightfield offset by half a tile, a body
     * whose collider never moved with its transform. All of them read as rendering faults until
     * this is on.
     */
    var showColliders: Boolean = false

    /**
     * Draws the Heightfield terrain mesh samples, Jolt samples, local normals, and axes together.
     *
     * This is intentionally separate from [showColliders]: the generic collider overlay answers
     * where bodies exist, while this answers whether the terrain mesh and its collision source use
     * the same coordinates and orientation.
     */
    var showTerrainDiagnostics: Boolean = false

    /** Draws every mesh as lines. A renderer toggle rather than an overlay, so it is the one
     * flag here that changes how the scene itself is drawn. */
    var wireframe: Boolean = false

    /** Whether distance fog is enabled in the debugger override. */
    var fogEnabled: Boolean = true

    /** Fog density factor, where 0f is clear and 0.05f is dense fog. */
    var fogDensity: Float = 0.005f

    /** Fog color (RGB). */
    var fogColor: com.awakekt.awake.core.color.Color = com.awakekt.awake.core.color.Color(0.7f, 0.75f, 0.8f, 1f)

    /** Whether the user has toggled or adjusted fog in the debugger UI. */
    var overrideFog: Boolean = false

    /** Copies these into the engine's own [WorldDebugSettings] and renderer state.
     *
     * Per frame rather than on change: a showcase switch rebuilds the world, and settings that
     * lived only on the old one would silently turn themselves off. */
    fun applyTo(world: World, renderer: Renderer, options: Set<ShowcaseDebugOption>) {
        resetUnavailable(options)
        val settings = world.debugSettings()
        settings.showBounds = showBounds
        settings.showInstanceBounds = showInstanceBounds
        settings.showShadowFrustum = showShadowCascades
        settings.cascadedShadows = cascadedShadows
        // A false debug toggle must survive EnvironmentSystem's authored-light update later in
        // the frame. `true` deliberately means "use the authored setting", not force shadows
        // onto a showcase that intentionally authored its directional light without them.
        settings.shadowsEnabledOverride = if (shadows) null else false
        settings.showOcclusion = showOcclusion
        settings.showLights = showLights
        renderer.wireframe = wireframe
        if (overrideFog) {
            settings.fogDensityOverride = if (fogEnabled) fogDensity else 0f
            settings.fogColorOverride = fogColor
        } else {
            settings.fogDensityOverride = null
            settings.fogColorOverride = null
        }
        drawOverlays(world, renderer)
    }

    /** Switching samples cannot leave an invisible debug choice affecting the next sample. */
    private fun resetUnavailable(options: Set<ShowcaseDebugOption>) {
        if (ShowcaseDebugOption.NavGrid !in options) showNavGrid = false
        if (ShowcaseDebugOption.Corridor !in options) showCorridor = false
        if (ShowcaseDebugOption.Colliders !in options) showColliders = false
        if (ShowcaseDebugOption.TerrainProbes !in options) showTerrainDiagnostics = false
    }

    /** Whether colliders owned the line buffer last frame, so turning them off can clear it once. */
    private var overlaysDrawn = false

    /**
     * Hands the renderer the collider wireframes, and otherwise leaves its line buffer alone.
     *
     * `drawDebugLines` replaces the buffer rather than appending, so writing an empty list every
     * frame would erase whatever a showcase driver had just drawn. Touching it only when there is
     * something to say -- or one last time when the toggle goes off -- keeps the two sources from
     * fighting.
     *
     * Known limit: a showcase that draws its own debug lines AND has physics bodies will overwrite
     * these, because it draws afterwards. No showcase does both today, and the fix is a merge
     * point rather than more ordering.
     */
    private fun drawOverlays(world: World, renderer: Renderer) {
        val lines = buildList {
            if (showColliders) addAll(physicsDebugLines(world))
            if (showTerrainDiagnostics) addAll(heightfieldDiagnosticLines(world))
        }
        if (lines.isNotEmpty() || overlaysDrawn) {
            renderer.drawDebugLines(lines)
            overlaysDrawn = lines.isNotEmpty()
        }
    }
}

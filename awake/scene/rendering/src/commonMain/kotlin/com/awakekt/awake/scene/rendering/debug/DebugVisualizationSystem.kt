/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.debug

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Grid
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.inverse
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.renderer.DEFAULT_SCENE_LIGHT
import com.awakekt.awake.render.renderer.LineSegment
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.boundsDebugLines
import com.awakekt.awake.render.renderer.cascadeShadowBoxes
import com.awakekt.awake.render.renderer.directionalShadowBox
import com.awakekt.awake.render.renderer.frustumDebugLines
import com.awakekt.awake.render.renderer.lightGizmoLines
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.CONSERVATIVE_ASPECT
import com.awakekt.awake.scene.rendering.Camera
import com.awakekt.awake.scene.rendering.Light
import com.awakekt.awake.scene.rendering.RenderSystem
import com.awakekt.awake.scene.rendering.mesh.InstancedMeshRenderer
import com.awakekt.awake.scene.rendering.mesh.LodGroup
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
import com.awakekt.awake.scene.rendering.spatial.Occluder
import kotlin.math.abs
import kotlin.math.min

/**
 * Draws the camera frustum and/or [MeshBounds] boxes as world-space wireframes, gated by
 * [WorldDebugSettings] -- kept separate from [RenderSystem] (which already does draw-call
 * assembly, culling, and LOD selection) so this stays a single-responsibility opt-in, same
 * "own component, own system" shape [InstancedMeshRenderer]/[LodGroup] already established
 * rather than growing [RenderSystem.update] further. Run this after [RenderSystem] each frame
 * -- both call `renderer.draw`/`drawDebugLines` independently, order between them doesn't
 * matter for correctness (only which one's lines end up in this frame's line buffer, and
 * [Renderer.drawDebugLines] replaces the whole buffer each call, not appends).
 */
class DebugVisualizationSystem(
    private val renderer: Renderer,
) : System {
    override fun update(world: World, delta: Float) {
        val settings = world.family<WorldDebugSettings>().components().firstOrNull() ?: return
        val lines = debugVisualizationLines(world, renderer, settings)
        if (lines.isNotEmpty()) renderer.drawDebugLines(lines)
    }
}

/**
 * The world-space wireframe lines [WorldDebugSettings]' toggles ask for -- pulled out of
 * [DebugVisualizationSystem.update] so a caller that ALSO has its own debug lines to draw this
 * frame (an external editor's gizmo handles) can merge both into one [Renderer.drawDebugLines] call instead
 * of two: that call replaces the whole line buffer rather than appending, so whichever caller
 * draws last would otherwise silently wipe the other's lines out -- exactly what made debug
 * toggles disappear the moment an entity was selected (a real gizmo handle only draws when
 * something is selected, and an external editor gizmo system runs after this one specifically so
 * a real drag isn't itself wiped).
 */
fun debugVisualizationLines(
    world: World,
    renderer: Renderer,
    settings: WorldDebugSettings,
): List<LineSegment> {
    if (!settings.hasAnyDebugLines()) {
        return emptyList()
    }
    val lines = ArrayList<LineSegment>()
    // Deliberately NOT primaryCamera -- see WorldDebugSettings.frustumTargetEntityId's own
    // doc comment for why drawing the viewport's own camera's frustum is invisible by
    // construction. No target (or a target with no Camera) draws nothing, rather than
    // falling back to primaryCamera and reintroducing that same invisible case.
    if (settings.showFrustum) {
        // The REAL viewport aspect, not CONSERVATIVE_ASPECT -- that constant is deliberately
        // wider than any real viewport (a safety margin for RenderSystem's own CPU-side
        // frustum-cull check, see its own doc comment), so reusing it here drew a visibly wider
        // box than the camera's actual fovY/aspect would ever really see. renderer.sceneViewport
        // is the same rect Studio's confineSceneTo(...) sets every frame; falls back to the
        // conservative constant only when nothing has set a real viewport (e.g. a full-window
        // render with no confined scene rect).
        val aspect = renderer.sceneViewport?.aspect ?: CONSERVATIVE_ASPECT
        settings.frustumTargetEntityId
            ?.let { targetId -> world.cameraOf(targetId) }
            ?.let {
                lines += frustumDebugLines(
                    it.lens.visualizedFarClamped(),
                    aspect,
                    FRUSTUM_COLOR,
                )
            }
    }
    if (settings.showBounds) {
        world.family<Transform, MeshBounds>().forEach { _, transform, bounds ->
            lines += boundsDebugLines(bounds.localBounds, transform.worldMatrix, BOUNDS_COLOR)
        }
    }
    // Visualizes which boxes are occluders, not per-entity occluded/visible state --
    // RenderSystem.lastOccludedCount already gives a numeric "did this cull anything"
    // signal, so this stays geometry-only, same scope showBounds already has.
    if (settings.showOcclusion) {
        world.family<Transform, Occluder>().forEach { _, transform, occluder ->
            lines += boundsDebugLines(occluder.localBounds, transform.worldMatrix, OCCLUDER_COLOR)
        }
    }
    if (settings.showLights || settings.showShadowFrustum) {
        // Falls back to DEFAULT_SCENE_LIGHT, same as RenderSystem.sceneLight() -- a scene
        // with no Light entity (most of Studio's examples) still shades with that default
        // direction, so the debugger should show the direction that's actually lighting it.
        val light = world.family<Light>().components().firstOrNull()
        val direction = light?.direction ?: DEFAULT_SCENE_LIGHT.direction
        if (settings.showLights) {
            lines += lightGizmoLines(
                directionalShadowBox(direction, renderer.clipSpace).eye,
                direction,
                LIGHT_COLOR,
            )
        }
        if (settings.showShadowFrustum) {
            lines += cascadeBoxLines(
                world,
                direction,
                renderer.clipSpace,
                // The same aspect RenderSystem fits cascades with, not the viewport's own: a box
                // drawn at a different aspect than the one sampled is a picture of a box nobody
                // renders from.
                CONSERVATIVE_ASPECT,
            )
        }
    }
    if (settings.showGrid) emitGridLines(lines, settings)
    if (settings.showAxisLines) emitAxisLines(lines, settings)
    return lines
}

private fun WorldDebugSettings.hasAnyDebugLines(): Boolean =
    showFrustum || showBounds || showOcclusion || showLights || showShadowFrustum || showGrid || showAxisLines

private fun emitGridLines(lines: MutableList<LineSegment>, settings: WorldDebugSettings) {
    val extent = settings.gridFadeDistance.coerceAtLeast(10f)
    val step = settings.gridScale.coerceAtLeast(0.1f)
    val gridPairs = Grid.generateGridLines(extent = extent, step = step)
    val majorInterval = 5
    gridPairs.forEachIndexed { index, (start, end) ->
        val isMajor = (index % majorInterval) == 0
        val color = if (isMajor) GRID_MAJOR_COLOR else GRID_LINE_COLOR
        lines += LineSegment(start, end, color)
    }
}

private fun emitAxisLines(lines: MutableList<LineSegment>, settings: WorldDebugSettings) {
    val extent = settings.gridFadeDistance.coerceAtLeast(50f)
    lines += LineSegment(Vec3f(-extent, 0f, 0f), Vec3f(extent, 0f, 0f), AXIS_X_COLOR)
    lines += LineSegment(Vec3f(0f, 0f, -extent), Vec3f(0f, 0f, extent), AXIS_Z_COLOR)
    lines += LineSegment(Vec3f(0f, 0f, 0f), Vec3f(0f, 1.5f, 0f), AXIS_Y_COLOR)
}

/** The [Camera] component on the entity with [entityId], or `null` when that entity has none --
 * same lookup `StudioShell.kt`'s own (Studio-private) `cameraOf` does, duplicated here since
 * this module is engine-neutral and can't depend on a Studio-only helper. */
private fun World.cameraOf(entityId: Int): Camera? {
    var found: Camera? = null
    family<Camera>().forEach { entity, camera -> if (entity.id == entityId) found = camera }
    return found
}

/** A frustum's own authored `far` is a clip plane, not a "camera scope of view" distance -- it's
 * routinely far larger than the scene it's looking at (rotating-cube's own camera authors 100
 * units against a ~10-20 unit scene), which draws a far quad that reads as "shooting off to
 * infinity" rather than a legible box. Visualization-only cap; the real far still governs actual
 * rendering (this only affects the debug wireframe's own far edge). */
private const val MAX_VISUALIZED_FRUSTUM_DISTANCE = 30f

private fun Lens.visualizedFarClamped(): Lens =
    Lens(
        eye = eye,
        center = center,
        up = up,
        fovYRadians = fovYRadians,
        near = near,
        far = min(far, MAX_VISUALIZED_FRUSTUM_DISTANCE),
    ).also { it.projection = projection }

private val FRUSTUM_COLOR = Color(r = 1f, g = 1f, b = 0f, a = 1f)
private val BOUNDS_COLOR = Color(r = 0f, g = 1f, b = 0f, a = 1f)
private val OCCLUDER_COLOR = Color(r = 1f, g = 0.5f, b = 0f, a = 1f)
private val LIGHT_COLOR = Color(r = 1f, g = 0.75f, b = 0f, a = 1f)
private val GRID_LINE_COLOR = Color(r = 0.25f, g = 0.25f, b = 0.28f, a = 0.45f)
private val GRID_MAJOR_COLOR = Color(r = 0.38f, g = 0.38f, b = 0.42f, a = 0.7f)
private val AXIS_X_COLOR = Color(r = 0.9f, g = 0.22f, b = 0.22f, a = 0.9f)
private val AXIS_Z_COLOR = Color(r = 0.22f, g = 0.45f, b = 0.95f, a = 0.9f)
private val AXIS_Y_COLOR = Color(r = 0.22f, g = 0.85f, b = 0.22f, a = 0.9f)

/**
 * One per cascade, near to far, so the overlay says WHICH box a shadow came from.
 *
 * A single colour would draw three nested boxes in one hue, which reads as one box with noisy
 * edges -- exactly the picture that hides a cascade fitted to the wrong slice.
 */
private val CASCADE_COLORS = listOf(
    Color(r = 1f, g = 0f, b = 0.75f, a = 1f),
    Color(r = 0.4f, g = 0.6f, b = 1f, a = 1f),
    Color(r = 0.2f, g = 1f, b = 0.8f, a = 1f),
    Color(r = 0.8f, g = 0.8f, b = 0.4f, a = 1f),
)

/**
 * The boxes the shadow pass ACTUALLY renders from, one per cascade.
 *
 * This used to draw `directionalShadowBox`'s fixed origin volume, which stopped being what the
 * renderer uses when cascades landed -- so the overlay claimed a box the shadows were not coming
 * from. A debug view that disagrees with the renderer is worse than none: it is evidence pointing
 * the wrong way.
 *
 * Fitted to the same camera `RenderSystem` fits them to, so what is drawn is what is sampled.
 */
private fun cascadeBoxLines(
    world: World,
    direction: Vec3f,
    clipSpace: com.awakekt.awake.core.math.ClipSpace,
    aspect: Float,
): List<LineSegment> {
    val camera = world.family<Camera>().components().firstOrNull()?.lens ?: return emptyList()
    return cascadeShadowBoxes(camera, aspect, direction, clipSpace)
        .flatMapIndexed { index, box ->
            // The box in ITS OWN space is the ortho volume; the view matrix's inverse puts that
            // volume back in the world, which is where a line has to be drawn.
            val half = 1f / box.projection.m00
            val depth = 2f / box.projection.m22
            val localBox = Aabb(Vec3f(-half, -half, 0f), Vec3f(half, half, -abs(depth)))
            box.view.inverse()
                ?.let { boundsDebugLines(localBox, it, CASCADE_COLORS[index % CASCADE_COLORS.size]) }
                .orEmpty()
        }
}

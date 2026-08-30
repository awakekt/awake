/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.ui.graphics.vector

import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.compose.ui.graphics.drawscope.DrawScope
import io.github.awakelab.awake.core.color.Color
import io.github.awakelab.awake.core.graphics2d.ColoredTriangleMesh
import io.github.awakelab.awake.core.graphics2d.merge
import io.github.awakelab.awake.core.graphics2d.tessellateFillAa
import io.github.awakelab.awake.core.graphics2d.tessellateStrokeAa
import io.github.awakelab.awake.core.math2d.Rectangle

/**
 * Tessellates one [ImageVector] once and redraws those triangles every frame after.
 *
 * Drawing a vector through `drawPath` hands the coalescer a shape to tessellate *per frame*, and an
 * icon's shape never changes: a 16px outline icon flattens to ~2,400 vertices, and Studio spent
 * 20.2 ms of a 43.9 ms frame tessellating 38 of them -- against 3.6 microseconds for the other 194
 * primitives in the same frame, 160 glyphs included.
 *
 * Compose caches vectors for exactly this reason and spells it `rememberVectorPainter`; it caches
 * the rendered layer where this caches the triangles, because this engine tessellates on the CPU.
 * The cache is per painter, and therefore per call site, as it is upstream: two icons of the same
 * image tessellate twice. Sharing them would need a key, and hashing a path's command list costs
 * about what tessellating it saves -- measured, 20.2 ms fell only to 8.5 ms keyed by value.
 */
class VectorPainter internal constructor(private val image: ImageVector) {
    private var slot: Rectangle? = null
    private var tint: Color? = null
    private var mesh: ColoredTriangleMesh? = null

    /**
     * Draws [image] into [slot], tessellating only when the slot or [tint] has moved.
     *
     * [tint] stands in for SVG's `currentColor`: a path carrying its own fill keeps it, and every
     * other path takes this.
     */
    fun draw(scope: DrawScope, slot: Rectangle, tint: Color) {
        scope.drawMesh(meshFor(slot, tint))
    }

    private fun meshFor(slot: Rectangle, tint: Color): ColoredTriangleMesh {
        val cached = mesh
        if (cached != null && this.slot == slot && this.tint == tint) return cached
        // fitTo scales the vector into the slot and snaps the centering offset to whole pixels --
        // an odd (slot - scaled) difference otherwise lands the glyph on a half-pixel and blurs
        // every edge, which shipped once as icons "not pixel perfect".
        val built = image.fitTo(slot).flatMap { vectorPath ->
            buildList {
                // SVG's default fill is `currentColor`. A path with an explicit stroke and no fill
                // is outline-only; every other path inherits the tint.
                if (vectorPath.fill != null || vectorPath.stroke == null) {
                    add(vectorPath.path.tessellateFillAa(vectorPath.fill ?: tint))
                }
                vectorPath.stroke?.let { add(vectorPath.path.tessellateStrokeAa(it, tint)) }
            }
        }.merge()
        this.slot = slot
        this.tint = tint
        mesh = built
        return built
    }
}

/**
 * A [VectorPainter] for [image] that survives the next pass.
 *
 * Keyed on the image, which a generated icon object holds as a `val` -- so the key check hits the
 * `this === other` arm of the data class's own equals rather than walking every path command.
 */
context(_: Composer)
fun rememberVectorPainter(image: ImageVector): VectorPainter = remember(image) { VectorPainter(image) }

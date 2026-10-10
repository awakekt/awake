/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.outline

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.command.MASK_LAYER_COUNT
import com.awakekt.awake.render.renderer.OutlineStyle
import com.awakekt.awake.scene.core.transform.Transform

/**
 * Outlines this entity's silhouette in [set]'s style: a line around its edge, drawn over the scene,
 * as an editor marks what is selected or under the pointer, or a game marks what can be picked up.
 * Needs a render plan with the mask pass and the outline overlay, as `ProjectRenderPlan` has.
 *
 * It outlines the entity's own mesh, skinned meshes in their pose. An imported model is a hierarchy
 * of meshes, so [World.outline] marks an entity and everything under it.
 *
 * @property set Which of [OutlineStyles]' styles to draw in: 0 or 1. Set 0 draws over set 1 where
 * the two meet.
 */
data class Outlined(var set: Int = 0) {
    init {
        require(set in 0 until MASK_LAYER_COUNT) { "An outline set is 0 to ${MASK_LAYER_COUNT - 1}; was $set." }
    }
}

/**
 * How each outline set is drawn, one style per set. One per world, on any entity; a world without
 * one draws [Default].
 *
 * @property styles Set 0's style, then set 1's.
 */
data class OutlineStyles(val styles: List<OutlineStyle> = DEFAULT_STYLES) {
    init {
        require(styles.size == MASK_LAYER_COUNT) { "There are $MASK_LAYER_COUNT outline sets; got ${styles.size} styles." }
    }

    /** [styles] packed as the mask sub-passes carry them, once. */
    internal val packed: List<FloatArray> by lazy { styles.map(OutlineStyle::packed) }

    /** The styles a world without its own draws. */
    companion object {
        /** An orange outline for set 0, the selection, and a fainter white one for set 1. */
        val Default = OutlineStyles()
    }
}

private val DEFAULT_STYLES = listOf(
    OutlineStyle(Color(1f, 0.6f, 0.1f, 1f), widthPixels = 3f),
    OutlineStyle(Color(1f, 1f, 1f, 0.7f), widthPixels = 2f),
)

/**
 * Outlines [root] and every entity parented under it in [set], or stops outlining them when [set]
 * is null. An imported model is such a hierarchy of meshes, which an editor's selection outlines
 * whole.
 */
fun World.outline(root: Entity, set: Int?) {
    val members = mutableSetOf(root)
    var grew = true
    while (grew) {
        grew = false
        queryEach<Transform> { entity, transform ->
            if (entity !in members && transform.parent in members) {
                members += entity
                grew = true
            }
        }
    }
    members.forEach { entity -> if (set == null) remove<Outlined>(entity) else add(entity, Outlined(set)) }
}

/** The styles the mask pass's layers carry this frame, or none when nothing is outlined. */
internal fun World.outlineMaskLayers(): List<FloatArray> {
    if (family<Outlined>().size == 0) return emptyList()
    var styles: OutlineStyles? = null
    queryEach<OutlineStyles> { _, found -> if (styles == null) styles = found }
    return (styles ?: OutlineStyles.Default).packed
}

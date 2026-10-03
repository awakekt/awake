/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.mesh

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.passes.uniforms.TextureAnimation
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

/**
 * Plays an entity's texture as a frame sheet of [columns] x [rows] cells, in reading order (left to
 * right, then top to bottom of the image), and scrolls it. The textured shader applies it to every
 * texture of the entity's material.
 *
 * @property columns Frame-sheet columns.
 * @property rows Frame-sheet rows.
 * @property frameCount Frames played, from the first; 0 means every cell.
 * @property framesPerSecond Playback rate; 0 holds the first frame.
 * @property scrollU UV units per second along U.
 * @property scrollV UV units per second along V, toward the bottom of the image.
 */
@Serializable
@SerialName("texture_animation")
data class SceneTextureAnimation(
    val columns: Int = 1,
    val rows: Int = 1,
    val frameCount: Int = 0,
    val framesPerSecond: Float = 0f,
    val scrollU: Float = 0f,
    val scrollV: Float = 0f,
) : SceneComponent {
    /** Every cell when [frameCount] is 0. */
    val frames: Int get() = if (frameCount == 0) columns * rows else frameCount

    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        if (columns < 1 || rows < 1) add(SceneValidationIssue(path, "columns and rows must be at least 1"))
        if (frameCount < 0 || frameCount > columns * rows) {
            add(SceneValidationIssue(path, "frameCount must be within 0..columns * rows"))
        }
        if (framesPerSecond < 0f || !framesPerSecond.isFinite()) {
            add(SceneValidationIssue(path, "framesPerSecond must be finite and >= 0"))
        }
        if (!scrollU.isFinite() || !scrollV.isFinite()) add(SceneValidationIssue(path, "the UV scroll must be finite"))
    }
}

/** Loads [SceneTextureAnimation] as the entity's [TextureAnimation], which the draw collector reads beside its [PbrMaterial]. */
object TextureAnimationBinding : SceneComponentBinding<TextureAnimation, SceneTextureAnimation> {
    override val componentClass: KClass<TextureAnimation> = TextureAnimation::class
    override val schemaClass: KClass<SceneTextureAnimation> = SceneTextureAnimation::class
    override val serializer = SceneTextureAnimation.serializer()

    override fun attachTyped(
        world: World,
        entity: Entity,
        component: SceneTextureAnimation,
        context: SceneResolutionContext,
    ) {
        world.add(
            entity,
            TextureAnimation(
                component.columns,
                component.rows,
                component.frames,
                component.framesPerSecond,
                component.scrollU,
                component.scrollV,
            ),
        )
    }

    override fun export(world: World, entity: Entity, component: TextureAnimation): SceneTextureAnimation =
        SceneTextureAnimation(
            component.columns,
            component.rows,
            component.frameCount,
            component.framesPerSecond,
            component.scrollU,
            component.scrollV,
        )
}

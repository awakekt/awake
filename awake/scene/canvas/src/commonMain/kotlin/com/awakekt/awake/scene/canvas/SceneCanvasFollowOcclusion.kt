/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.core.schema.PropertyRange
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

/**
 * How a scene answers occlusion for the followers whose `followRules.occlusion` asks: by a physics
 * ray from the camera to each followed point, within a budget. A played project casts them when
 * the scene has a physics world and either a follower asks or the scene has a
 * `canvas_follow_occlusion`; the rays are what this tunes.
 *
 * @property raysPerFrame The most rays cast a frame, across every follower; at least 1. The rest
 *   keep their last answer for another frame, the longest waiting cast first.
 * @property reuseFrames How many frames an answer stands before its follower's point is cast
 *   again; at least 1. A few frames hides nothing a player notices, and divides the rays.
 * @property layer The one collision layer the rays hit, such as the static world's 0, so walls and
 *   terrain hide labels and moving bodies do not; null hits every layer.
 */
data class CanvasFollowOcclusion(
    val raysPerFrame: Int = DEFAULT_RAYS_PER_FRAME,
    val reuseFrames: Int = DEFAULT_REUSE_FRAMES,
    val layer: Int? = null,
) {
    /** What is wrong with these settings, each as `field message`; empty when nothing is. */
    fun problems(): List<String> = listOfNotNull(
        "raysPerFrame must be at least 1".takeIf { raysPerFrame < 1 },
        "reuseFrames must be at least 1".takeIf { reuseFrames < 1 },
        "layer must not be negative".takeIf { layer != null && layer < 0 },
    )

    /** The defaults. */
    companion object {
        /** Rays a frame unless set. */
        const val DEFAULT_RAYS_PER_FRAME: Int = 32

        /** Frames an answer stands unless set: a tenth of a second at 60 frames a second. */
        const val DEFAULT_REUSE_FRAMES: Int = 6

        /** What a scene with no `canvas_follow_occlusion` casts with. */
        val Default: CanvasFollowOcclusion = CanvasFollowOcclusion()
    }
}

/**
 * The saved form of a [CanvasFollowOcclusion]: the budget of the physics rays that answer the
 * scene's followers' occlusion. A scene has one, on any node; without one, a scene whose followers
 * ask for occlusion casts with [CanvasFollowOcclusion.Default].
 *
 * @property raysPerFrame The most rays cast a frame, across every follower; at least 1.
 * @property reuseFrames How many frames an answer stands before it is cast again; at least 1.
 * @property layer The one collision layer the rays hit, such as the static world's 0; null hits
 *   every layer.
 */
@Serializable
@SerialName("canvas_follow_occlusion")
data class SceneCanvasFollowOcclusion(
    @PropertyRange(min = 1.0) val raysPerFrame: Int = CanvasFollowOcclusion.DEFAULT_RAYS_PER_FRAME,
    @PropertyRange(min = 1.0) val reuseFrames: Int = CanvasFollowOcclusion.DEFAULT_REUSE_FRAMES,
    @PropertyRange(min = 0.0) val layer: Int? = null,
) : SceneComponent {
    override fun validate(path: String): List<SceneValidationIssue> =
        CanvasFollowOcclusion(raysPerFrame, reuseFrames, layer).problems().map { SceneValidationIssue(path, "canvas_follow_occlusion.$it") }
}

/** Loads and saves a [CanvasFollowOcclusion] as the `canvas_follow_occlusion` scene component. */
object CanvasFollowOcclusionBinding : SceneComponentBinding<CanvasFollowOcclusion, SceneCanvasFollowOcclusion> {
    override val componentClass: KClass<CanvasFollowOcclusion> = CanvasFollowOcclusion::class
    override val schemaClass: KClass<SceneCanvasFollowOcclusion> = SceneCanvasFollowOcclusion::class
    override val serializer = SceneCanvasFollowOcclusion.serializer()

    override fun attachTyped(world: World, entity: Entity, component: SceneCanvasFollowOcclusion, context: SceneResolutionContext) {
        world.add(entity, CanvasFollowOcclusion(component.raysPerFrame, component.reuseFrames, component.layer))
    }

    override fun export(world: World, entity: Entity, component: CanvasFollowOcclusion): SceneCanvasFollowOcclusion =
        SceneCanvasFollowOcclusion(component.raysPerFrame, component.reuseFrames, component.layer)
}

/** The scene's [CanvasFollowOcclusion], from the first node that has one, or null when none does. */
fun World.canvasFollowOcclusion(): CanvasFollowOcclusion? {
    val all = family<CanvasFollowOcclusion>()
    return if (all.size == 0) null else all.componentAt(0)
}

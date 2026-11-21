/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.mesh

import com.awakekt.awake.ecs.ComponentStore
import com.awakekt.awake.ecs.EcsTag
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.ecs.componentStore
import com.awakekt.awake.ecs.componentType
import com.awakekt.awake.scene.binding.SceneBinding
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.rendering.animation.SocketAttachmentComponent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Keeps an entity at full detail whatever its distance from the camera (#1805): its [LodGroup] draws
 * level 0, and so does every group that follows it; `AnimationSystem` poses its skinned model every
 * frame, whatever `AnimationUpdateRate` would hold it for.
 *
 * Data on the entity, so a game adds and removes it as the player's target, party or mount changes,
 * and a scene document carries it as `{ "component": "detail_pin" }`. An entity whose enabled
 * [SocketAttachmentComponent] follows a pinned one is pinned too, so a pinned character's weapon or
 * rider keeps its detail: up to [MAX_ATTACHMENT_DEPTH] attachments deep.
 */
data object DetailPin : EcsTag

/** Scene form of [DetailPin]: `{ "component": "detail_pin" }`, bound by the generated `DetailPinBinding`. */
@Serializable
@SceneBinding(DetailPin::class)
@SerialName("detail_pin")
data object SceneDetailPin : SceneComponent {
    override val allowsMultiplePerNode: Boolean get() = false
}

/** How many socket attachments a pin passes through: a rider on a mount on a pinned character is three. */
const val MAX_ATTACHMENT_DEPTH: Int = 4

/** The frame's view of which entities are pinned: read the stores once, then ask per entity. Allocation-free. */
internal class DetailPins {
    private var pins: ComponentStore<DetailPin>? = null
    private var sockets: ComponentStore<SocketAttachmentComponent>? = null

    /** Reads [world]'s stores for this frame. */
    fun attach(world: World): DetailPins {
        pins = world.componentStore(PIN)
        sockets = world.componentStore(SOCKET)
        return this
    }

    /** Whether [entity] is pinned, or follows a pinned entity through enabled socket attachments. */
    fun isPinned(entity: Entity): Boolean {
        val pins = pins ?: return false
        // Non-null throughout: an `Entity?` would box the value class once a hop, per entity per frame.
        var current = entity
        var hops = 0
        var pinned = false
        var attached = true
        while (!pinned && attached && hops <= MAX_ATTACHMENT_DEPTH) {
            pinned = pins.get(current) != null
            val socket = sockets?.get(current)
            attached = socket != null && socket.enabled
            if (socket != null) current = socket.targetEntity
            hops++
        }
        return pinned
    }

    private companion object {
        val PIN = componentType<DetailPin>()
        val SOCKET = componentType<SocketAttachmentComponent>()
    }
}

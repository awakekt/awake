/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.mesh

import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.render.command.NO_MASK_LAYER
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.pipeline.AlphaMode
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.spatial.CullVerdict

/**
 * What [SceneDrawCollector] kept of each mesh entity from the frame before, so a prop that has
 * not moved is not classified, packed into a command and bounded again every frame.
 *
 * A cull verdict holds while the entity's transform version, bounds and shadow opt-in hold and
 * the frame's [epoch][com.awakekt.awake.scene.rendering.spatial.FrameCulling.epoch] is unchanged.
 * A command holds while its renderer, packed material, alpha and mask layer hold on top of that.
 * Each slot owns its command and box, so a hit allocates nothing and a miss allocates once.
 *
 * Indexed by entity id: a world's ids are dense, and a boxed key would allocate per lookup.
 */
internal class StaticDrawCache {
    private var slots = arrayOfNulls<Slot>(INITIAL_SLOTS)
    private var frame = 0

    /** Slots dropped when their entity was not collected for a while, so a destroyed entity's mesh is not kept alive. */
    fun beginFrame() {
        if (++frame % SWEEP_INTERVAL != 0) return
        for (i in slots.indices) {
            val slot = slots[i] ?: continue
            if (frame - slot.seen > SWEEP_INTERVAL) slots[i] = null
        }
    }

    fun clear() = slots.fill(null)

    /** The slot for [entity], or null when its id is too large to index. */
    fun slotFor(entity: Entity): Slot? {
        val id = entity.id
        if (id < 0 || id >= MAX_SLOTS) return null
        if (id >= slots.size) slots = slots.copyOf(maxOf(id + 1, slots.size * 2).coerceAtMost(MAX_SLOTS))
        val slot = slots[id] ?: Slot().also { slots[id] = it }
        slot.seen = frame
        return slot
    }

    internal class Slot {
        var seen = 0
        private var owner = -1L
        private var transform: Transform? = null
        private var worldVersion = 0
        private var bounds: MeshBounds? = null
        private var casts = false
        private var epoch = 0
        var verdict = CullVerdict.Visible
            private set

        private var commandVerdict: CullVerdict? = null
        private var renderer: MeshRenderer? = null
        private var extras: FloatArray? = null
        private var alphaMode = AlphaMode.Opaque
        private var alphaCutoff = 0f
        private var maskLayer = NO_MASK_LAYER
        private val box = Aabb(Vec3f(0f, 0f, 0f), Vec3f(0f, 0f, 0f))
        val command: RenderDrawCommand = createEmptyDrawCommand()

        /** Whether the verdict kept for this entity is still the answer. A transform no system has built (version 0) is never kept. */
        fun verdictHolds(entity: Entity, transform: Transform, bounds: MeshBounds, casts: Boolean, epoch: Int): Boolean =
            epoch != 0 && transform.worldVersion != 0 && entity.packed == owner && transform === this.transform &&
                transform.worldVersion == worldVersion && bounds === this.bounds && casts == this.casts && epoch == this.epoch

        fun keepVerdict(entity: Entity, transform: Transform, bounds: MeshBounds, casts: Boolean, epoch: Int, verdict: CullVerdict) {
            val sameEntity = entity.packed == owner && transform === this.transform && transform.worldVersion == worldVersion &&
                bounds === this.bounds && casts == this.casts
            if (!sameEntity || verdict != commandVerdict) commandVerdict = null
            owner = entity.packed
            this.transform = transform
            worldVersion = transform.worldVersion
            this.bounds = bounds
            this.casts = casts
            this.epoch = epoch
            this.verdict = verdict
        }

        /** Whether [command] already holds this entity's draw for the inputs given. */
        fun commandHolds(renderer: MeshRenderer, extras: FloatArray, alphaMode: AlphaMode, alphaCutoff: Float, maskLayer: Int): Boolean =
            commandVerdict == verdict && renderer === this.renderer && extras === this.extras && alphaMode == this.alphaMode &&
                alphaCutoff == this.alphaCutoff && maskLayer == this.maskLayer

        /** Records the inputs [command] was just built from. */
        fun keepCommand(renderer: MeshRenderer, extras: FloatArray, alphaMode: AlphaMode, alphaCutoff: Float, maskLayer: Int) {
            commandVerdict = verdict
            this.renderer = renderer
            this.extras = extras
            this.alphaMode = alphaMode
            this.alphaCutoff = alphaCutoff
            this.maskLayer = maskLayer
        }

        /** The box [command]'s world bounds are written into. */
        fun boundsBox(): Aabb = box
    }

    private companion object {
        const val INITIAL_SLOTS = 1024
        const val MAX_SLOTS = 1 shl 20
        const val SWEEP_INTERVAL = 600
    }
}

/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.binding

import com.awakekt.awake.ecs.Poolable
import com.awakekt.awake.scene.document.SceneUnknownComponent

/**
 * The components an entity's scene node held that nothing installed provides, kept as data. No
 * system reads them; [SceneComponentRegistry.exportComponents] writes them back, so a world an
 * editor saves still holds them.
 */
class SceneUnknownComponents : Poolable {
    private val kept = ArrayList<SceneUnknownComponent>()

    /** The kept components, in the order the scene listed them. */
    val components: List<SceneUnknownComponent> get() = kept

    internal fun add(component: SceneUnknownComponent) {
        kept += component
    }

    override fun reset() {
        kept.clear()
    }
}

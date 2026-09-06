/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio.fixture

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.math.Aabb

/** CPU-side mesh bounds recorded while Studio creates its fixture assets, for viewport picking. */
internal object StudioFixtureBounds {
    private val bounds = mutableMapOf<String, Aabb>()

    operator fun get(meshId: String): Aabb? = bounds[meshId]

    /**
     * Every mesh the fixture has registered, in registration order.
     *
     * This is Studio's asset list, and it is an honest one rather than a placeholder: these are the
     * meshes actually loaded and drawable. It is not the whole of what an asset browser eventually
     * wants -- no textures, no materials, no thumbnails -- and the dock says so by showing what
     * exists rather than pretending the tab is unavailable.
     */
    val meshIds: List<String> get() = bounds.keys.toList()

    fun register(meshId: String, geometry: MeshGeometry) {
        geometry.bounds?.let { bounds[meshId] = it }
    }
}

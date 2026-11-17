/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuDrawRequest

/**
 * Copies of the scene's draws for finding which field makes their compile cost more than plain
 * copies do (#1621). [plain] carries only the mesh, material, a fresh model and bounds, `transparent`
 * and `shadowsOnly`, as the synthetic frame does. Each other copy adds one field from the scene's
 * own draw to that, and [all] is the data-class copy, which shares every reference the draw holds.
 */
internal object CompileFieldCopies {
    /** Fresh commands with only what the synthetic frame sets. */
    fun plain(draws: List<GpuDrawRequest>): List<GpuDrawRequest> = draws.map { plainCopy(it) }

    /** The data-class copy of each draw: every field, and the same model, bounds and extras objects. */
    fun all(draws: List<GpuDrawRequest>): List<GpuDrawRequest> = draws.map { it.copy() }

    /** One named list per field, each a [plain] copy plus that field from the scene's draw. */
    fun byField(draws: List<GpuDrawRequest>): List<Pair<String, List<GpuDrawRequest>>> = FIELDS.map { (name, add) ->
        name to draws.map { draw -> plainCopy(draw).also { add(it, draw) } }
    }

    private fun plainCopy(draw: GpuDrawRequest) = GpuDrawRequest(
        draw.mesh,
        draw.material,
        model = Mat4().set(draw.model),
        worldBounds = draw.worldBounds?.let { Aabb(Vec3f(it.min.x, it.min.y, it.min.z), Vec3f(it.max.x, it.max.y, it.max.z)) },
        transparent = draw.transparent,
        shadowsOnly = draw.shadowsOnly,
    )

    /** Each field the scene's collector sets and [plain] leaves at its default, copied onto a plain copy. */
    private val FIELDS: List<Pair<String, (GpuDrawRequest, GpuDrawRequest) -> Unit>> = listOf(
        "extras" to { to, from -> to.extraUniformFloats = from.extraUniformFloats },
        "vertexAnimation" to { to, from -> to.vertexAnimation = from.vertexAnimation },
        "time" to { to, from -> to.timeSeconds = from.timeSeconds },
        "cullMode" to { to, from -> to.cullMode = from.cullMode },
        "alpha" to { to, from ->
            to.alphaMode = from.alphaMode
            to.alphaCutoff = from.alphaCutoff
        },
        "castsShadows" to { to, from -> to.castsShadows = from.castsShadows },
        "maskLayer" to { to, from -> to.maskLayer = from.maskLayer },
        "coverings" to { to, from -> to.exemptFromCoverings = from.exemptFromCoverings },
        "additive" to { to, from -> to.additive = from.additive },
        "fade" to { to, from -> to.fadeOpacity = from.fadeOpacity },
        "sharedModel" to { to, from -> to.model = from.model },
        "sharedBounds" to { to, from -> to.worldBounds = from.worldBounds },
    )
}

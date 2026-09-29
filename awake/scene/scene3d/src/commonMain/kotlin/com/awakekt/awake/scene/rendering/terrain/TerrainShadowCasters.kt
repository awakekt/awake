/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.terrain

import com.awakekt.awake.asset.terrain.Heightmap
import com.awakekt.awake.asset.terrain.toPositionNormalColorMesh
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.createMaterial

/**
 * Terrain's shadow caster: a heightmap mesh per visible [TerrainComponent], drawn into the shadow
 * maps only.
 *
 * The terrain the camera sees is clipmap rings displaced on the GPU, which no shadow pass draws,
 * so hills cast nothing without this. The mesh follows the heightmap's own placement, as the
 * clipmap does, is rebuilt when the heightmap or its revision changes, and is freed when its
 * terrain goes.
 */
internal class TerrainShadowCasters(private val renderer: Renderer) {
    private class Caster(val heightmap: Heightmap, val revision: Int, val mesh: Mesh)

    private val casters = HashMap<Entity, Caster>()
    private val seen = HashSet<Entity>()
    private var material: Material? = null

    fun collect(world: World, into: MutableList<RenderDrawCommand>) {
        seen.clear()
        world.family<TerrainComponent>().forEach { entity, terrain ->
            seen += entity
            if (!terrain.isVisible) return@forEach
            val caster = casterFor(entity, terrain)
            into += RenderDrawCommand(caster.mesh, material(), model = IDENTITY, shadowsOnly = true)
        }
        if (casters.size > seen.size) {
            casters.keys.filterNot(seen::contains).forEach { casters.remove(it)?.mesh?.destroy() }
        }
    }

    private fun casterFor(entity: Entity, terrain: TerrainComponent): Caster {
        val current = casters[entity]
        if (current != null && current.heightmap === terrain.heightmap && current.revision == terrain.revision) return current
        current?.mesh?.destroy()
        val mesh = renderer.createMesh(terrain.heightmap.casterResolution().toPositionNormalColorMesh())
        return Caster(terrain.heightmap, terrain.revision, mesh).also { casters[entity] = it }
    }

    private fun material(): Material =
        material ?: renderer.createMaterial(MaterialUniformLayouts.LitShadow).also { material = it }

    private companion object {
        val IDENTITY = Mat4()
    }
}

/**
 * This heightmap, or every n-th sample of it so neither side passes [MAX_CASTER_SAMPLES].
 *
 * Known limit: a plain stride, which drops up to n - 1 edge samples and lets a coarse caster sit
 * above the surface between its samples; clipmap-style LOD in the depth pass is the upgrade.
 */
internal fun Heightmap.casterResolution(): Heightmap {
    val stride = maxOf((width - 1 + MAX_CASTER_SAMPLES - 2) / (MAX_CASTER_SAMPLES - 1), (depth - 1 + MAX_CASTER_SAMPLES - 2) / (MAX_CASTER_SAMPLES - 1), 1)
    if (stride == 1) return this
    val coarseWidth = (width - 1) / stride + 1
    val coarseDepth = (depth - 1) / stride + 1
    val samples = copySamples()
    val coarse = FloatArray(coarseWidth * coarseDepth) { index ->
        samples[(index / coarseWidth) * stride * width + (index % coarseWidth) * stride]
    }
    val scale = scale
    scale.x *= stride
    scale.z *= stride
    return Heightmap(coarse, coarseWidth, coarseDepth, scale, origin)
}

/** 513 x 513 samples is about half a million triangles per cascade. */
internal const val MAX_CASTER_SAMPLES = 513

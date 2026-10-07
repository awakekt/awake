/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.examples

import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.math.ViewportScaling
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.authoring.infrastructure.gameplayInput
import com.awakekt.awake.scene.binding.Scene
import com.awakekt.awake.scene.physics.PhysicsBody
import com.awakekt.awake.scene.rendering.Camera
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.scene.scene2d.Tilemap

/** Sample-owned pixels and controls; simulation and animation use the standard scene systems. */
internal object Runtime2dExampleDriver {
    private var camera: Camera? = null
    private var tiles: Tilemap? = null
    private var bodies = emptyList<Entity>()
    private var viewportKeyDown = false
    private var tileKeyDown = false

    /** Original geometric cells: ground, marker, and two inset sprite frames, in atlas order. */
    fun texture(): TextureAsset {
        val colors = arrayOf(intArrayOf(20, 180, 60), intArrayOf(30, 70, 220), intArrayOf(240, 180, 20), intArrayOf(220, 40, 40))
        val size = 8
        val pixels = ByteArray(size * size * colors.size * 4)
        for (y in 0 until size) {
            for (x in 0 until size * colors.size) {
                val frame = x / size
                val offset = (y * size * colors.size + x) * 4
                val color = colors[frame]
                for (channel in 0 until 3) pixels[offset + channel] = color[channel].toByte()
                val margin = frame >= 2 && (x % size == 0 || x % size == size - 1 || y == 0 || y == size - 1)
                pixels[offset + 3] = if (margin) 0 else -1
            }
        }
        return TextureAsset(pixels, size * colors.size, size)
    }

    fun attach(instance: Scene, runtime: SceneAppLifecycleRuntime) {
        camera = instance.roots.single { it.name == "camera" }.let { runtime.world.get<Camera>(it.entity) }
        tiles = instance.roots.single { it.name == "tiles" }.let { runtime.world.get<Tilemap>(it.entity) }
        bodies = instance.roots.filter { runtime.world.get<PhysicsBody>(it.entity) != null }.map { it.entity }
        viewportKeyDown = false
        tileKeyDown = false
    }

    fun advance(runtime: SceneAppLifecycleRuntime) {
        val input = runtime.gameplayInput()
        val viewportDown = input.isDown(Key.V)
        if (viewportDown && !viewportKeyDown) {
            camera?.let { camera ->
                camera.viewport?.let { view ->
                    camera.viewport = view.copy(scaling = ViewportScaling.entries[(view.scaling.ordinal + 1) % ViewportScaling.entries.size])
                }
            }
        }
        viewportKeyDown = viewportDown
        val tileDown = input.isDown(Key.T)
        if (tileDown && !tileKeyDown) {
            tiles?.let { map -> map.setTile(7, 3, if (map.grid[7, 3] == -1) 1 else -1) }
        }
        tileKeyDown = tileDown
    }

    /** Remove bodies before scene entity destruction; switching away must leave no invisible floor. */
    fun detach(runtime: SceneAppLifecycleRuntime) {
        bodies.forEach { ShowcasePhysics.physicsSystem?.destroyBody(runtime.world, it) }
        bodies = emptyList()
        camera = null
        tiles = null
        viewportKeyDown = false
        tileKeyDown = false
    }
}

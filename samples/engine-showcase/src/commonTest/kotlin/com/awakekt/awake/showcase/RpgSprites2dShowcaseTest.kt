/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase

import com.awakekt.awake.core.host.readResourceBytes
import com.awakekt.awake.core.image.createBitmap
import com.awakekt.awake.core.image.toRgba8Bytes
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.runtime.DefaultSceneComponentResolvers
import com.awakekt.awake.scene.scene2d.SceneSprite
import com.awakekt.awake.scene.scene2d.Sprite
import com.awakekt.awake.scene.scene2d.SpriteClipSystem
import com.awakekt.awake.showcase.examples.RpgSprites2dExampleAssets
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RpgSprites2dShowcaseTest {
    init {
        DefaultSceneComponentResolvers.install()
    }

    @Test
    fun realManifestSelectsSeparateHeroAndEnemyRowsAndAdvancesBoth() = runTest {
        RpgSprites2dExampleAssets.preload()
        val entry = EngineShowcases.single { it.id == "rpg-sprites-2d" }
        val document = RpgSprites2dExampleAssets.importScene(SceneLoader.loadFromResource(entry.scenePath))
        val schemas = document.nodes.flatMap { it.components }.filterIsInstance<SceneSprite>()
        assertEquals(2, schemas.size)
        assertTrue(schemas.all { it.columns == 4 && it.rows == 2 })
        assertEquals(listOf(false, true), schemas.map { it.flipX }, "the enemy faces the ranger through authored flipping")
        val world = World()
        SceneLoader.decode(SceneLoader.encode(document)).instantiate(world = world)
        assertEquals(listOf(0, 4), world.frames())
        SpriteClipSystem().update(world, 0.25f)
        assertEquals(listOf(1, 5), world.frames())
        SpriteClipSystem().update(world, 0.75f)
        assertEquals(listOf(0, 4), world.frames(), "each character loops inside its own atlas row")
    }

    @Test
    fun realAtlasHasEightPopulatedTransparentCellsWithClearBorders() = runTest {
        val bitmap = createBitmap(readResourceBytes("assets/sprites/woodland-rivals/sprite-sheet-alpha.png"))
        assertEquals(1024, bitmap.width)
        assertEquals(512, bitmap.height)
        val bytes = bitmap.toRgba8Bytes()
        for (row in 0 until 2) {
            for (column in 0 until 4) {
                var occupied = 0
                for (y in 0 until 256) {
                    for (x in 0 until 256) {
                        val alpha = bytes[((row * 256 + y) * bitmap.width + column * 256 + x) * 4 + 3].toInt() and 0xff
                        if (alpha > 0) occupied++
                        if (x !in 24 until 232 || y !in 24 until 232) assertEquals(0, alpha, "cell $column,$row has artwork outside its safe margin")
                    }
                }
                assertTrue(occupied > 1000, "cell $column,$row must contain a real character")
            }
        }
    }

    private fun World.frames(): List<Int> = buildList { queryEach<Sprite> { _, sprite -> add(sprite.frame) } }.sorted()
}

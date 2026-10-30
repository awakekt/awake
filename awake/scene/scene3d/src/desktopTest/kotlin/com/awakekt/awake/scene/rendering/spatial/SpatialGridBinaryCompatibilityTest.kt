/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.spatial

import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.passes.ShadowCasterVolume
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Code compiled against 0.27 links against the scene-side grid by its JVM names, never through
 * Kotlin source. So this reaches every signature 0.27 published by name, through reflection, the
 * way that bytecode does: a missing class or a changed descriptor fails here, not in a consumer.
 */
class SpatialGridBinaryCompatibilityTest {

    private val gridClass: Class<*> = Class.forName("$PACKAGE.SpatialGrid")
    private val collection = Collection::class.java
    private val float = Float::class.javaPrimitiveType
    private val int = Int::class.javaPrimitiveType

    @Test
    fun theOldGridClassIsConstructedAndQueriedByItsJvmNames() {
        val grid = gridClass.getConstructor(float).newInstance(32f)
        gridClass.getMethod("insert", int, Aabb::class.java).invoke(grid, 7, Aabb(Vec3f(-1f, 0f, -1f), Vec3f(1f, 2f, 1f)))

        val box = Aabb(Vec3f(-2f, -2f, -2f), Vec3f(2f, 2f, 2f))
        val hits = gridClass.getMethod("queryAabb", Aabb::class.java, collection).invoke(grid, box, ArrayList<Int>())
        assertEquals(listOf(7), hits)
        assertEquals(listOf(7), gridClass.getMethod("queryRange", Vec3f::class.java, float, collection).invoke(grid, Vec3f(0f, 1f, 0f), 1f, ArrayList<Int>()))
        assertEquals(
            emptyList<Int>(),
            gridClass.getMethod("queryShadowCasters", ShadowCasterVolume::class.java, collection).invoke(grid, ShadowCasterVolume(), ArrayList<Int>()),
        )
        assertEquals(1, gridClass.getMethod("getSize").invoke(grid))
        assertEquals(setOf(7), gridClass.getMethod("getIds").invoke(grid))
        assertEquals(32f, gridClass.getMethod("getCellSize").invoke(grid))

        gridClass.getMethod("remove", int).invoke(grid, 7)
        gridClass.getMethod("clear").invoke(grid)
        assertEquals(0, gridClass.getMethod("getSize").invoke(grid))
    }

    @Test
    fun theOldConstructorsAndStaticFieldsKeepTheirJvmShape() {
        val defaulted = gridClass.getConstructor().newInstance()
        assertEquals(DEFAULT_CELL_SIZE, gridClass.getMethod("getCellSize").invoke(defaulted))
        // What a Kotlin caller compiled against 0.27 emits for `SpatialGrid()`.
        val marker = Class.forName("kotlin.jvm.internal.DefaultConstructorMarker")
        val viaDefaults = gridClass.getConstructor(float, int, marker).newInstance(0f, 1, null)
        assertEquals(DEFAULT_CELL_SIZE, gridClass.getMethod("getCellSize").invoke(viaDefaults))

        assertEquals(4, gridClass.getField("INITIAL_COLUMN_CAPACITY").getInt(null))
        assertEquals(64, gridClass.getField("INITIAL_STAMP_CAPACITY").getInt(null))
        assertEquals(DEFAULT_CELL_SIZE, Class.forName("$PACKAGE.SpatialGridKt").getField("DEFAULT_SPATIAL_CELL_SIZE").getFloat(null))
    }

    @Test
    fun aSpatialIndexBuiltFromTheOldGridByItsJvmNamesIsMaintained() {
        val grid = gridClass.getConstructor(float).newInstance(32f)
        val indexClass = SpatialIndex::class.java
        val index = indexClass.getConstructor(gridClass).newInstance(grid)
        assertSame(grid, indexClass.getMethod("getGrid").invoke(index))
        assertSame(grid, indexClass.getMethod("component1").invoke(index))
        assertSame(grid, indexClass.getMethod("getGrid").invoke(indexClass.getMethod("copy", gridClass).invoke(index, grid)))

        val world = World()
        world.add(world.create(), index)
        val prop = world.create()
        world.add(prop, Transform(worldMatrix = Mat4().translate(5f, 0f, 5f)))
        world.add(prop, MeshBounds(Aabb(Vec3f(-1f, -1f, -1f), Vec3f(1f, 1f, 1f))))
        SpatialIndexSystem().update(world, 0f)

        val area = Aabb(Vec3f(0f, -2f, 0f), Vec3f(10f, 2f, 10f))
        assertEquals(listOf(prop.id), gridClass.getMethod("queryAabb", Aabb::class.java, collection).invoke(grid, area, ArrayList<Int>()))
    }

    private companion object {
        const val PACKAGE = "com.awakekt.awake.scene.rendering.spatial"
        const val DEFAULT_CELL_SIZE = 64f
    }
}

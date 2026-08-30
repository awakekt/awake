/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ecs.benchmark

import io.github.awakelab.awake.scene.core.components.Transform
import io.github.awakelab.awake.scene.rendering.components.MeshRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PureArchetypeStorageTest {
    @Test
    fun tagMigrationMovesRowsAndRepairsSwapLocations() {
        val storage = PureArchetypeStorage(entityCapacity = 3)
        val transforms = List(3) { Transform() }
        val renderers = List(3) {
            MeshRenderer(FakeGpuObjects.mesh, FakeGpuObjects.material)
        }
        repeat(3) { entityId ->
            storage.add(entityId, transforms[entityId], renderers[entityId], PRIMARY_TAG)
        }

        storage.removeTag(entityId = 0, tagBit = PRIMARY_TAG)
        storage.removeTag(entityId = 2, tagBit = PRIMARY_TAG)
        storage.addTag(entityId = 0, tagBit = PRIMARY_TAG)

        assertEquals(3, storage.size)
        assertEquals(2, storage.matchingEntityCount(PRIMARY_TAG, excludedTags = 0))
        assertStoredReferences(storage, transforms, renderers)
    }

    @Test
    fun repeatedTagOperationsAreIdempotent() {
        val storage = PureArchetypeStorage(entityCapacity = 1)
        storage.add(
            entityId = 0,
            transform = Transform(),
            meshRenderer = MeshRenderer(FakeGpuObjects.mesh, FakeGpuObjects.material),
        )

        storage.addTag(entityId = 0, tagBit = PRIMARY_TAG)
        storage.addTag(entityId = 0, tagBit = PRIMARY_TAG)
        storage.removeTag(entityId = 0, tagBit = PRIMARY_TAG)
        storage.removeTag(entityId = 0, tagBit = PRIMARY_TAG)

        assertEquals(1, storage.size)
        assertEquals(0, storage.matchingEntityCount(PRIMARY_TAG, excludedTags = 0))
    }

    @Test
    fun fragmentedTablesApplyRequiredAndExcludedMasks() {
        val storage = PureArchetypeStorage(entityCapacity = 256, tableCapacityHint = 1)
        repeat(256) { signature ->
            storage.add(
                entityId = signature,
                transform = Transform(),
                meshRenderer = MeshRenderer(FakeGpuObjects.mesh, FakeGpuObjects.material),
                tagSignature = signature,
            )
        }

        assertEquals(256, storage.tableCount)
        assertEquals(64, storage.matchingEntityCount(requiredTags = 1, excludedTags = 2))
        assertTrue(storage.remove(255))
        assertFalse(storage.remove(255))
        assertEquals(255, storage.size)
    }

    private fun assertStoredReferences(
        storage: PureArchetypeStorage,
        transforms: List<Transform>,
        renderers: List<MeshRenderer>,
    ) {
        val seen = BooleanArray(transforms.size)
        repeat(storage.tableCount) { tableIndex ->
            val table = storage.tableAt(tableIndex)
            repeat(table.size) { row ->
                val entityId = table.entityAt(row)
                assertSame(transforms[entityId], table.transformAt(row))
                assertSame(renderers[entityId], table.meshRendererAt(row))
                seen[entityId] = true
            }
        }
        assertTrue(seen.all { it })
    }
}

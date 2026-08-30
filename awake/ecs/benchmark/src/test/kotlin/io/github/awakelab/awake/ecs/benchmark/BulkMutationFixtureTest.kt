/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ecs.benchmark

import io.github.awakelab.awake.ecs.Entity
import io.github.awakelab.awake.scene.core.components.Transform
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Guards the two things the phase-0 batch/density tables actually depend on: the batch really is
 * `batchSize` mutations, and `densityPercent` really is the share of them that move an entity in
 * and out of the maintained family.
 */
class BulkMutationFixtureTest {
    @Test
    fun fullDensityBatchMovesEveryEntityThroughFamily2() {
        val fixture = BulkMutationFixture(batchSize = 100, densityPercent = 100, familyArity = 2)
        val before = fixture.resultSize()
        assertEquals(BulkMutationFixture.WORLD_SIZE, before)

        removeBatch(fixture)
        assertEquals(before - 100, fixture.resultSize())

        addBatch(fixture)
        assertEquals(before, fixture.resultSize())
    }

    @Test
    fun onePercentDensityBatchMovesOnePercentThroughFamily2() {
        val fixture = BulkMutationFixture(batchSize = 100, densityPercent = 1, familyArity = 2)
        val before = fixture.resultSize()
        assertEquals(BulkMutationFixture.WORLD_SIZE / 100, before)

        removeBatch(fixture)
        assertEquals(before - 1, fixture.resultSize())

        addBatch(fixture)
        assertEquals(before, fixture.resultSize())
    }

    @Test
    fun arityZeroCountsTheWholeTransformStoreAtEitherDensity() {
        listOf(1, 100).forEach { density ->
            val fixture = BulkMutationFixture(batchSize = 10, densityPercent = density, familyArity = 0)
            assertEquals(BulkMutationFixture.WORLD_SIZE, fixture.resultSize())
            removeBatch(fixture)
            assertEquals(BulkMutationFixture.WORLD_SIZE - 10, fixture.resultSize())
        }
    }

    private fun removeBatch(fixture: BulkMutationFixture) {
        fixture.batch.forEach { packed ->
            fixture.world.remove<Transform>(Entity(packed), fixture.transformTypeId)
        }
    }

    private fun addBatch(fixture: BulkMutationFixture) {
        fixture.batch.forEach { packed ->
            fixture.world.add<Transform>(Entity(packed), fixture.transformTypeId)
        }
    }
}

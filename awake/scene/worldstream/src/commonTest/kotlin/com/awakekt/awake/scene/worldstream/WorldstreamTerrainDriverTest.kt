/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.worldstream

import com.awakekt.awake.scene.runtime.TerrainDriver
import com.awakekt.awake.scene.world.WorldPartitionConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class WorldstreamTerrainDriverTest {

    @Test
    fun driverConformsToTerrainDriverContract() {
        val driver: TerrainDriver = WorldstreamTerrainDriver(
            config = WorldPartitionConfig(
                cellSize = 256.0f,
                loadingRadius = 512.0f,
                unloadRadius = 768.0f,
            ),
        )
        assertNotNull(driver)
        assertFalse(driver.isMounted)
    }

    @Test
    fun defaultConfigurationValuesMatchProductionScale() {
        val driver = WorldstreamTerrainDriver()
        assertEquals(512.0f, driver.config.cellSize)
        assertEquals(1024.0f, driver.config.loadingRadius)
        assertEquals(1536.0f, driver.config.unloadRadius)
        assertEquals("lit-shadow", driver.materialName)
    }
}

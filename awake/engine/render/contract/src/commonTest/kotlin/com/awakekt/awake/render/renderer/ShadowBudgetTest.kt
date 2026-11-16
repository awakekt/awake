/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.renderer

import com.awakekt.awake.render.pipeline.GpuDeviceClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** What each quality preset spends on shadows (#1688). */
class ShadowBudgetTest {
    @Test
    fun aDesktopKeepsTheShadowsItDrewBeforeUnderEveryPresetButPerformance() {
        for (device in listOf(GpuDeviceClass.Discrete, GpuDeviceClass.Integrated, GpuDeviceClass.Unknown)) {
            for (quality in listOf(RenderQuality.Auto, RenderQuality.High, RenderQuality.Balanced)) {
                assertEquals(ShadowBudget.Default, RenderQualityPolicy.shadowBudget(quality, device), "$quality on $device")
            }
            assertEquals(ShadowBudget.Reduced, RenderQualityPolicy.shadowBudget(RenderQuality.Performance, device))
        }
        assertEquals(ShadowBudget(cascades = 3, mapSize = 2048), ShadowBudget.Default, "what every renderer drew before")
    }

    @Test
    fun aPhoneUnderAutoDrawsTwoCascadesOfAThousandTexels() {
        assertEquals(ShadowBudget(cascades = 2, mapSize = 1024), RenderQualityPolicy.shadowBudget(RenderQuality.Auto, GpuDeviceClass.Mobile))
        assertEquals(ShadowBudget.Reduced, RenderQualityPolicy.shadowBudget(RenderQuality.Auto, GpuDeviceClass.Software))
        assertEquals(ShadowBudget.Default, RenderQualityPolicy.shadowBudget(RenderQuality.High, GpuDeviceClass.Mobile), "High is High anywhere")
    }

    @Test
    fun aBudgetOutsideWhatTheShadersSampleIsRefused() {
        assertFailsWith<IllegalArgumentException> { ShadowBudget(cascades = 0) }
        assertFailsWith<IllegalArgumentException> { ShadowBudget(cascades = MAX_SHADOW_CASCADES + 1) }
        assertFailsWith<IllegalArgumentException> { ShadowBudget(mapSize = 1000) }
        assertFailsWith<IllegalArgumentException> { ShadowBudget(mapSize = 128) }
        assertEquals(4, ShadowBudget(cascades = 4, mapSize = 4096).cascades)
    }
}

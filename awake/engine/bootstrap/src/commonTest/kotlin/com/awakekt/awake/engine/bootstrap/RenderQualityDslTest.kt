/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.bootstrap

import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.bootstrap.dsl.renderQuality
import com.awakekt.awake.engine.platform.dsl.service
import com.awakekt.awake.engine.platform.quality.RenderQualityPersistence
import com.awakekt.awake.engine.platform.quality.RenderQualitySettings
import com.awakekt.awake.render.renderer.RenderQuality
import com.awakekt.awake.render.renderer.RenderQualityBudgets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

class RenderQualityDslTest {

    @Test
    fun renderQualityRegistersOneSessionScopedService() {
        var saved: RenderQuality? = RenderQuality.Balanced
        val budgets = RenderQualityBudgets(balancedShortEdge = 900)
        lateinit var registered: RenderQualitySettings
        val first = app {
            registered = renderQuality {
                persistence = object : RenderQualityPersistence {
                    override fun load() = saved
                    override fun save(quality: RenderQuality) {
                        saved = quality
                    }
                }
                this.budgets = budgets
            }
        }
        val second = app { renderQuality { initial = RenderQuality.High } }

        val settings = first.service<RenderQualitySettings>()
        assertSame(registered, settings)
        assertEquals(RenderQuality.Balanced, settings?.quality?.value, "loaded through the callback")
        assertSame(budgets, settings?.budgets)
        settings?.select(RenderQuality.Performance)
        assertEquals(RenderQuality.Performance, saved, "saved through the callback")

        val other = second.service<RenderQualitySettings>()
        assertNotSame(settings, other)
        assertEquals(RenderQuality.High, other?.quality?.value)
    }

    @Test
    fun renderQualityIsOptInAndRegisteredOnce() {
        assertNull(app {}.service<RenderQualitySettings>())
        assertFailsWith<IllegalStateException> {
            app {
                renderQuality()
                renderQuality()
            }
        }
    }
}

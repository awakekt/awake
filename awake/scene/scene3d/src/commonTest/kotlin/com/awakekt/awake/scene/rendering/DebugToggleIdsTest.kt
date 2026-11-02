/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.diagnostics.DebugFlags
import com.awakekt.awake.core.diagnostics.DebugRegistry
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.passes.uniforms.RenderDebugView
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.rendering.debug.DebugToggleIds
import com.awakekt.awake.scene.rendering.debug.DebugVisualizationSystem
import com.awakekt.awake.scene.rendering.debug.WorldDebugSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DebugToggleIdsTest {
    @Test
    fun eachIdTurnsOnItsToggle() {
        val settings = DebugToggleIds.applyTo(
            WorldDebugSettings(),
            DebugFlags.parse("bounds,instance-bounds,occluders,lights,shadow-frustum,grid,axes,skeleton,wireframe"),
        )

        assertTrue(settings.showBounds)
        assertTrue(settings.showInstanceBounds)
        assertTrue(settings.showOcclusion)
        assertTrue(settings.showLights)
        assertTrue(settings.showShadowFrustum)
        assertTrue(settings.showGrid)
        assertTrue(settings.showAxisLines)
        assertTrue(settings.showSkeleton)
        assertTrue(settings.showWireframe)
        assertFalse(settings.showFrustum, "the frustum needs a target camera, so it has no id")
    }

    @Test
    fun aViewIdPicksItsRenderView() {
        assertEquals("view-world-normals", DebugToggleIds.viewId(RenderDebugView.WorldNormals))
        assertEquals("view-shadow-visibility", DebugToggleIds.viewId(RenderDebugView.ShadowVisibility))

        val settings = DebugToggleIds.applyTo(WorldDebugSettings(), setOf("view-linear-depth"))

        assertEquals(RenderDebugView.LinearDepth, settings.renderDebugView)
    }

    @Test
    fun idsLeaveTheRestOfTheSettingsAlone() {
        val authored = WorldDebugSettings(showLights = true, gridScale = 2f)

        DebugToggleIds.applyTo(authored, setOf("grid"))

        assertTrue(authored.showLights)
        assertTrue(authored.showGrid)
        assertEquals(2f, authored.gridScale)
        assertFalse(authored.showBounds)
    }

    @Test
    fun aWorldWithoutSettingsGetsThemOnlyWhenAnIdAsks() {
        val world = World()

        assertNull(DebugToggleIds.applyTo(world, setOf("culling", "cells")), "other sources' ids are not toggles")
        assertEquals(0, world.family<WorldDebugSettings>().size)

        val added = DebugToggleIds.applyTo(world, setOf("bounds"))

        assertSame(added, world.family<WorldDebugSettings>().components().first())
        assertTrue(added!!.showBounds)
    }

    @Test
    fun theVisualizationSystemAppliesTheRegistrysIdsToTheWorldOnce() {
        val world = World()
        val system = DebugVisualizationSystem(NoopRenderer(), { null }, DebugRegistry(setOf("grid", "wireframe")))

        system.update(world, FRAME)
        val settings = world.family<WorldDebugSettings>().components().first()
        assertTrue(settings.showGrid)
        assertTrue(settings.showWireframe)

        settings.showGrid = false
        system.update(world, FRAME)

        assertFalse(settings.showGrid, "applied once, so a debug panel can still turn it off")
        assertEquals(1, world.family<WorldDebugSettings>().size)
    }

    @Test
    fun withNoToggleIdTheSystemAddsNothing() {
        val world = World()

        DebugVisualizationSystem(NoopRenderer(), { null }, DebugRegistry(setOf("culling"))).update(world, FRAME)

        assertEquals(0, world.family<WorldDebugSettings>().size)
    }

    private companion object {
        const val FRAME = 1f / 60f
    }
}

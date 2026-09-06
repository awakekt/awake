/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.provides
import com.awakekt.awake.compose.ui.layout.MeasurePolicy
import com.awakekt.awake.compose.ui.layout.composeInto
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.ecs.World
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/**
 * The locals carry what they were given, and say so loudly when nothing gave them anything.
 *
 * The failure case is the one worth a test: a default `World` would let an overlay compose against
 * an empty one and draw a blank screen, which reads as a content bug rather than a missing
 * provider -- and would be found by looking at pixels rather than by an exception.
 */
/** The smallest root that measures: this file is about locals, not layout. */
private val EmptyRoot = MeasurePolicy { _, constraints ->
    layout(constraints.minWidth, constraints.minHeight) {}
}

class SceneLocalsTest {

    private fun compose(content: context(com.awakekt.awake.compose.runtime.Composer) () -> Unit) {
        composeInto(LayoutNode(EmptyRoot), content)
    }

    @Test
    fun aProvidedWorldReachesTheContent() {
        val world = World()
        var seen: World? = null

        compose {
            CompositionLocalProvider(LocalWorld provides world) { seen = LocalWorld.current }
        }

        assertSame(world, seen)
    }

    @Test
    fun readingAnUnprovidedWorldFails() {
        assertFailsWith<IllegalStateException> {
            compose { LocalWorld.current }
        }
    }

    @Test
    fun frameStatsDefaultToZeroRatherThanFailing() {
        // Unlike World, stats have an honest empty value: a preview with no runtime shows 0 fps,
        // which is true rather than misleading.
        var seen = -1f

        compose { seen = LocalFrameStats.current.fps }

        assertEquals(0f, seen)
    }
}

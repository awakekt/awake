/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.docs

// --8<-- [start:imports]
import com.awakekt.awake.asset.shaderpack.PackShaderSets
import com.awakekt.awake.asset.shaders.RenderPlan
import com.awakekt.awake.asset.shaders.ScenePipeline
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.render.pipeline.PipelineKey
// --8<-- [end:imports]
import com.awakekt.awake.asset.shaders.RenderBackend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// --8<-- [start:app]
/** The app: one window, and a hook that runs every frame. */
fun firstWindow(): AwakeAppLifecycle = app {
    window {
        title = "Hello AwakeKt"
        size(1280, 720)
    }
    render { frame ->
        // frame.delta is the seconds since the last frame.
    }
}
// --8<-- [end:app]

// --8<-- [start:plan]
/** What the app draws with: the lit, shadowed shader that ships in the shader pack. */
val GameRenderPlan = RenderPlan(
    primary = ScenePipeline(
        key = PipelineKey.Primary,
        shaders = PackShaderSets.LitShadow,
        vertexFormat = VertexFormat.PositionNormalColor,
        materialBindings = GroupBindings.UniformOnlyMaterial,
    ),
    depthPrePassShaderSet = PackShaderSets.ShadowDepth,
)
// --8<-- [end:plan]

/** The "Your first window" tutorial includes the app and plan above; this keeps them honest. */
class FirstWindowDocsSampleTest {

    @Test
    fun theWindowIsConfiguredAsThePageSays() {
        val config = firstWindow().windowConfig

        assertEquals("Hello AwakeKt", config.title)
        assertEquals(1280, config.width)
        assertEquals(720, config.height)
    }

    @Test
    fun thePlanResolvesForBothBackends() {
        for (backend in RenderBackend.entries) {
            assertTrue(GameRenderPlan.toPipelineRequests(backend).any { it.key == PipelineKey.Primary })
        }
    }
}

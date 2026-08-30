/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.shaders

import io.github.awakelab.awake.core.geometry.VertexFormat
import io.github.awakelab.awake.render.pipeline.PipelineKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Narrowing's failure mode is a scene that renders less than it was asked to, without erroring --
 * so every case here asserts what was dropped AND that the drop was reported.
 */
class RenderPlanNarrowingTest {

    @Test
    fun aBackendThatRunsEverythingChangesNothingAndSaysNothing() {
        val reports = mutableListOf<String>()

        // One instance, compared to itself: ShaderStages has identity equality (see
        // PipelineRegistry), so two `plan()` calls are never equal however identical they read.
        val plan = plan()

        val narrowed = plan.narrowedTo(capable(), reports::add)

        assertEquals(plan, narrowed)
        assertTrue(reports.isEmpty(), "Nothing was dropped, so nothing should have been reported")
    }

    @Test
    fun anUnsupportedPipelineIsDroppedAndReportedByName() {
        val reports = mutableListOf<String>()

        val narrowed = plan().narrowedTo(
            capable(supports = { it.key != SKINNED }),
            reports::add,
        )

        assertEquals(listOf(TEXTURED), narrowed.scenePipelines.map { it.key })
        assertTrue(
            reports.single().contains("$SKINNED"),
            "A dropped pipeline has to name itself, or a scene renders less with no way to " +
                "find out why. Reported: $reports",
        )
    }

    @Test
    fun aDepthPrePassIsDroppedWhenNothingCanSampleIt() {
        val reports = mutableListOf<String>()

        val narrowed = plan().narrowedTo(capable(depthPrePass = false), reports::add)

        assertNull(narrowed.depthPrePassShaderSet)
        assertTrue(reports.single().contains("depth pre-pass"), "Reported: $reports")
    }

    @Test
    fun aBackendThatCannotRunThePrimaryPipelineFailsRatherThanRenderingNothing() {
        // Not a capability gap: every mesh without its own pipeline falls back to the primary,
        // so narrowing it away is a scene that draws nothing at all.
        assertFailsWith<IllegalArgumentException> {
            plan().narrowedTo(capable(supports = { false })) {}
        }
    }

    private fun capable(
        depthPrePass: Boolean = true,
        supports: (ScenePipeline) -> Boolean = { true },
    ) = RenderCapabilities(RenderBackend.WebGpu, depthPrePass, supports)

    private fun plan() = RenderPlan(
        primary = pipeline(PipelineKey.Primary, VertexFormat.PositionNormalColor),
        scenePipelines = listOf(
            pipeline(SKINNED, VertexFormat.PositionNormalColorSkin),
            pipeline(TEXTURED, VertexFormat.PositionNormalColorUv),
        ),
        depthPrePassShaderSet = shaderSet("shadow_depth"),
    )

    private fun pipeline(key: PipelineKey, format: VertexFormat) =
        ScenePipeline(key = key, shaders = shaderSet("lit_shadow"), vertexFormat = format)

    private companion object {
        val SKINNED = PipelineKey.Format(VertexFormat.PositionNormalColorSkin)
        val TEXTURED = PipelineKey.Format(VertexFormat.PositionNormalColorUv)
    }
}

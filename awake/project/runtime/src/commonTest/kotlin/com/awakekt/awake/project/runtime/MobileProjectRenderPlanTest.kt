/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.asset.shaderpack.PackShaderSets
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.pipeline.PipelineKey
import kotlin.test.Test
import kotlin.test.assertEquals

class MobileProjectRenderPlanTest {
    @Test
    fun mobileProjectRenderPlanSubstitutesLeanMeshShadersAndPreservesOtherPipelines() {
        assertEquals(PackShaderSets.LitShadowNoDeposit, MobileProjectRenderPlan.primary.shaders)

        val byKey = MobileProjectRenderPlan.scenePipelines.associateBy { it.key }
        assertEquals(PackShaderSets.TexturedSimpleLit, byKey[PipelineKey.Format(VertexFormat.PositionNormalColorUv)]?.shaders)
        assertEquals(PackShaderSets.SkinnedTexturedSimpleLit, byKey[PipelineKey.Format(VertexFormat.PositionNormalColorUvSkin)]?.shaders)
        assertEquals(PackShaderSets.SkinnedNoDeposit, byKey[PipelineKey.Format(VertexFormat.PositionNormalColorSkin)]?.shaders)
        assertEquals(PackShaderSets.InstancedNoDeposit, byKey[PipelineKey.Instanced]?.shaders)
        assertEquals(
            PackShaderSets.InstancedTexturedTintedSimpleLit,
            byKey[PipelineKey.InstancedFormat(VertexFormat.PositionNormalColorUv)]?.shaders,
        )

        // Content features, depth casters, particle, sprite, world text, and trail pipelines remain intact.
        assertEquals(ProjectRenderPlan.contentFeatures, MobileProjectRenderPlan.contentFeatures)
        assertEquals(ProjectRenderPlan.depthPrePassShaderSet, MobileProjectRenderPlan.depthPrePassShaderSet)
        assertEquals(ProjectRenderPlan.depthPrePassVariants, MobileProjectRenderPlan.depthPrePassVariants)
        assertEquals(ProjectRenderPlan.depthPrePassKeyedVariants, MobileProjectRenderPlan.depthPrePassKeyedVariants)
        assertEquals(ProjectRenderPlan.scenePipelines.size, MobileProjectRenderPlan.scenePipelines.size)
    }
}

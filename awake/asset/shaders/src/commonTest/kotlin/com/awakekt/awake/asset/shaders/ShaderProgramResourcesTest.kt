/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaders

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.render.pipeline.PipelineKey
import com.awakekt.awake.render.pipeline.ShaderSource
import com.awakekt.awake.render.pipeline.entryPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ShaderProgramResourcesTest {

    @Test
    fun conventionBasedShaderSetMapsTriangleAcrossBackends() {
        val shaders = shaderSet("triangle", emptyMap())

        val vulkanVertex = assertIs<ShaderSource.ResourcePath>(shaders.vulkan[ShaderStage.VERTEX])
        val vulkanFragment = assertIs<ShaderSource.ResourcePath>(shaders.vulkan[ShaderStage.FRAGMENT])
        // WGSL on both halves: Vulkan ships source and compiles it through naga at load.
        assertEquals("assets/shader/vulkan/triangle.wgsl", vulkanVertex.path)
        assertEquals("assets/shader/vulkan/triangle.wgsl", vulkanFragment.path)
        assertEquals("vertexMain", vulkanVertex.entryPoint)
        assertEquals("fragmentMain", vulkanFragment.entryPoint)

        val webGpuVertex = assertIs<ShaderSource.ResourcePath>(shaders.webGpu[ShaderStage.VERTEX])
        val webGpuFragment = assertIs<ShaderSource.ResourcePath>(shaders.webGpu[ShaderStage.FRAGMENT])
        assertEquals("assets/shader/webgpu/triangle.wgsl", webGpuVertex.path)
        assertEquals("assets/shader/webgpu/triangle.wgsl", webGpuFragment.path)
        assertEquals("vertexMain", webGpuVertex.entryPoint)
        assertEquals("fragmentMain", webGpuFragment.entryPoint)
        assertEquals(true, shaders.webGpu.bindingsMetadataAvailable)
        assertEquals(emptyMap(), shaders.webGpu.bindingsByGroup)
    }

    @Test
    fun explicitShaderSetKeepsPerBackendEscapeHatch() {
        val shaders = shaderSet(
            vulkan = ShaderStages.graphics(
                vertex = ShaderSource.ResourcePath("assets/shader/vulkan/custom.vert.spv", entryPoint = "appVertex"),
                fragment = ShaderSource.ResourcePath("assets/shader/vulkan/custom.frag.spv", entryPoint = "appFragment"),
                bindingsByGroup = emptyMap(),
            ),
            webGpu = ShaderStages.graphics(
                vertex = ShaderSource.ResourcePath("assets/shader/webgpu/custom.wgsl", entryPoint = "vsMain"),
                fragment = ShaderSource.ResourcePath("assets/shader/webgpu/custom.wgsl", entryPoint = "fsMain"),
                bindingsByGroup = emptyMap(),
            ),
        )

        val vulkanVertex = assertIs<ShaderSource.ResourcePath>(shaders.vulkan[ShaderStage.VERTEX])
        val vulkanFragment = assertIs<ShaderSource.ResourcePath>(shaders.vulkan[ShaderStage.FRAGMENT])
        assertEquals("assets/shader/vulkan/custom.vert.spv", vulkanVertex.path)
        assertEquals("assets/shader/vulkan/custom.frag.spv", vulkanFragment.path)
        assertEquals("appVertex", vulkanVertex.entryPoint)
        assertEquals("appFragment", vulkanFragment.entryPoint)

        val webGpuVertex = assertIs<ShaderSource.ResourcePath>(shaders.webGpu[ShaderStage.VERTEX])
        val webGpuFragment = assertIs<ShaderSource.ResourcePath>(shaders.webGpu[ShaderStage.FRAGMENT])
        assertEquals("assets/shader/webgpu/custom.wgsl", webGpuVertex.path)
        assertEquals("vsMain", webGpuVertex.entryPoint)
        assertEquals("fsMain", webGpuFragment.entryPoint)
    }

    @Test
    fun resourcePathEntryPointExtensionReadsRegardlessOfVariant() {
        val resourcePath = ShaderSource.ResourcePath("x.wgsl", entryPoint = "vsMain")
        val precompiled = ShaderSource.PrecompiledBinary(byteArrayOf(1, 2, 3), entryPoint = "csMain")
        val inline = ShaderSource.InlineText("fn main() {}")

        assertEquals("vsMain", resourcePath.entryPoint)
        assertEquals("csMain", precompiled.entryPoint)
        assertEquals("main", inline.entryPoint)
    }

    @Test
    fun scenePipelineRequestsPreserveBindingMetadata() {
        val bindings = mapOf(0 to GroupBindings.UniformOnlyMaterial)
        val stages = ShaderStages.graphics(
            vertex = ShaderSource.InlineText("vertex"),
            fragment = ShaderSource.InlineText("fragment"),
            bindingsByGroup = bindings,
        )
        val shaders = ShaderSet(vulkan = stages, webGpu = stages)

        val request = listOf(
            ScenePipeline(
                key = PipelineKey.Primary,
                shaders = shaders,
                vertexFormat = VertexFormat.PositionColor,
            ),
        ).toRequests { it.webGpu }.single()

        assertEquals(bindings, request.spec.bindingsByGroup)
        assertEquals(true, request.spec.bindingsMetadataAvailable)
    }
}

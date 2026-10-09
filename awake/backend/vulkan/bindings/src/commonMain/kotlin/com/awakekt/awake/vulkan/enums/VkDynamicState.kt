/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums

/**
 * Pipeline state that is set while recording a command buffer instead of being baked into the
 * pipeline (`VkDynamicState`).
 *
 * @property value The raw integer value Vulkan uses for this constant.
 */
enum class VkDynamicState(val value: Int) {
    /** Set with `vkCmdSetViewport` when recording, instead of being fixed in the pipeline. */
    VK_DYNAMIC_STATE_VIEWPORT(0),

    /** Set with `vkCmdSetScissor` when recording, instead of being fixed in the pipeline. */
    VK_DYNAMIC_STATE_SCISSOR(1),

    /** Set with `vkCmdSetLineWidth` when recording, instead of being fixed in the pipeline. */
    VK_DYNAMIC_STATE_LINE_WIDTH(2),

    /** Set with `vkCmdSetDepthBias` when recording, instead of being fixed in the pipeline. */
    VK_DYNAMIC_STATE_DEPTH_BIAS(3),

    /** Set with `vkCmdSetBlendConstants` when recording, instead of being fixed in the pipeline. */
    VK_DYNAMIC_STATE_BLEND_CONSTANTS(4),

    /** Set with `vkCmdSetDepthBounds` when recording, instead of being fixed in the pipeline. */
    VK_DYNAMIC_STATE_DEPTH_BOUNDS(5),

    /**
     * Set with `vkCmdSetStencilCompareMask` when recording, instead of being fixed in the pipeline.
     */
    VK_DYNAMIC_STATE_STENCIL_COMPARE_MASK(6),

    /**
     * Set with `vkCmdSetStencilWriteMask` when recording, instead of being fixed in the pipeline.
     */
    VK_DYNAMIC_STATE_STENCIL_WRITE_MASK(7),

    /**
     * Set with `vkCmdSetStencilReference` when recording, instead of being fixed in the pipeline.
     */
    VK_DYNAMIC_STATE_STENCIL_REFERENCE(8),

    /** Set with `vkCmdSetCullMode` when recording, instead of being fixed in the pipeline. */
    VK_DYNAMIC_STATE_CULL_MODE(1000267000),

    /** Set with `vkCmdSetFrontFace` when recording, instead of being fixed in the pipeline. */
    VK_DYNAMIC_STATE_FRONT_FACE(1000267001),

    /**
     * Set with `vkCmdSetPrimitiveTopology` when recording, instead of being fixed in the pipeline.
     */
    VK_DYNAMIC_STATE_PRIMITIVE_TOPOLOGY(1000267002),

    /**
     * Set with `vkCmdSetViewportWithCount` when recording, instead of being fixed in the pipeline.
     */
    VK_DYNAMIC_STATE_VIEWPORT_WITH_COUNT(1000267003),

    /**
     * Set with `vkCmdSetScissorWithCount` when recording, instead of being fixed in the pipeline.
     */
    VK_DYNAMIC_STATE_SCISSOR_WITH_COUNT(1000267004),

    /**
     * The vertex binding strides are supplied with `vkCmdBindVertexBuffers2` instead of the
     * pipeline.
     */
    VK_DYNAMIC_STATE_VERTEX_INPUT_BINDING_STRIDE(1000267005),

    /**
     * Set with `vkCmdSetDepthTestEnable` when recording, instead of being fixed in the pipeline.
     */
    VK_DYNAMIC_STATE_DEPTH_TEST_ENABLE(1000267006),

    /**
     * Set with `vkCmdSetDepthWriteEnable` when recording, instead of being fixed in the pipeline.
     */
    VK_DYNAMIC_STATE_DEPTH_WRITE_ENABLE(1000267007),

    /** Set with `vkCmdSetDepthCompareOp` when recording, instead of being fixed in the pipeline. */
    VK_DYNAMIC_STATE_DEPTH_COMPARE_OP(1000267008),

    /**
     * Set with `vkCmdSetDepthBoundsTestEnable` when recording, instead of being fixed in the
     * pipeline.
     */
    VK_DYNAMIC_STATE_DEPTH_BOUNDS_TEST_ENABLE(1000267009),

    /**
     * Set with `vkCmdSetStencilTestEnable` when recording, instead of being fixed in the pipeline.
     */
    VK_DYNAMIC_STATE_STENCIL_TEST_ENABLE(1000267010),

    /** Set with `vkCmdSetStencilOp` when recording, instead of being fixed in the pipeline. */
    VK_DYNAMIC_STATE_STENCIL_OP(1000267011),

    /**
     * Set with `vkCmdSetRasterizerDiscardEnable` when recording, instead of being fixed in the
     * pipeline.
     */
    VK_DYNAMIC_STATE_RASTERIZER_DISCARD_ENABLE(1000377001),

    /**
     * Set with `vkCmdSetDepthBiasEnable` when recording, instead of being fixed in the pipeline.
     */
    VK_DYNAMIC_STATE_DEPTH_BIAS_ENABLE(1000377002),

    /**
     * Set with `vkCmdSetPrimitiveRestartEnable` when recording, instead of being fixed in the
     * pipeline.
     */
    VK_DYNAMIC_STATE_PRIMITIVE_RESTART_ENABLE(1000377004),

    /**
     * Set with `vkCmdSetViewportWScalingNV` when recording, instead of being fixed in the pipeline.
     */
    VK_DYNAMIC_STATE_VIEWPORT_W_SCALING_NV(1000087000),

    /**
     * Set with `vkCmdSetDiscardRectangleEXT` when recording, instead of being fixed in the
     * pipeline.
     */
    VK_DYNAMIC_STATE_DISCARD_RECTANGLE_EXT(1000099000),

    /**
     * Set with `vkCmdSetSampleLocationsEXT` when recording, instead of being fixed in the pipeline.
     */
    VK_DYNAMIC_STATE_SAMPLE_LOCATIONS_EXT(1000143000),

    /**
     * Set with `vkCmdSetRayTracingPipelineStackSizeKHR` when recording, instead of being fixed in
     * the pipeline.
     */
    VK_DYNAMIC_STATE_RAY_TRACING_PIPELINE_STACK_SIZE_KHR(1000347000),

    /**
     * Set with `vkCmdSetViewportShadingRatePaletteNV` when recording, instead of being fixed in the
     * pipeline.
     */
    VK_DYNAMIC_STATE_VIEWPORT_SHADING_RATE_PALETTE_NV(1000164004),

    /**
     * Set with `vkCmdSetCoarseSampleOrderNV` when recording, instead of being fixed in the
     * pipeline.
     */
    VK_DYNAMIC_STATE_VIEWPORT_COARSE_SAMPLE_ORDER_NV(1000164006),

    /**
     * Set with `vkCmdSetExclusiveScissorNV` when recording, instead of being fixed in the pipeline.
     */
    VK_DYNAMIC_STATE_EXCLUSIVE_SCISSOR_NV(1000205001),

    /**
     * Set with `vkCmdSetFragmentShadingRateKHR` when recording, instead of being fixed in the
     * pipeline.
     */
    VK_DYNAMIC_STATE_FRAGMENT_SHADING_RATE_KHR(1000226000),

    /** Set with `vkCmdSetLineStippleEXT` when recording, instead of being fixed in the pipeline. */
    VK_DYNAMIC_STATE_LINE_STIPPLE_EXT(1000259000),

    /** Set with `vkCmdSetVertexInputEXT` when recording, instead of being fixed in the pipeline. */
    VK_DYNAMIC_STATE_VERTEX_INPUT_EXT(1000352000),

    /**
     * Set with `vkCmdSetPatchControlPointsEXT` when recording, instead of being fixed in the
     * pipeline.
     */
    VK_DYNAMIC_STATE_PATCH_CONTROL_POINTS_EXT(1000377000),

    /** Set with `vkCmdSetLogicOpEXT` when recording, instead of being fixed in the pipeline. */
    VK_DYNAMIC_STATE_LOGIC_OP_EXT(1000377003),

    /**
     * Set with `vkCmdSetColorWriteEnableEXT` when recording, instead of being fixed in the
     * pipeline.
     */
    VK_DYNAMIC_STATE_COLOR_WRITE_ENABLE_EXT(1000381000),

    /** Alias of [VK_DYNAMIC_STATE_CULL_MODE]; both names carry the same value. */
    VK_DYNAMIC_STATE_CULL_MODE_EXT(VK_DYNAMIC_STATE_CULL_MODE.value),

    /** Alias of [VK_DYNAMIC_STATE_FRONT_FACE]; both names carry the same value. */
    VK_DYNAMIC_STATE_FRONT_FACE_EXT(VK_DYNAMIC_STATE_FRONT_FACE.value),

    /** Alias of [VK_DYNAMIC_STATE_PRIMITIVE_TOPOLOGY]; both names carry the same value. */
    VK_DYNAMIC_STATE_PRIMITIVE_TOPOLOGY_EXT(VK_DYNAMIC_STATE_PRIMITIVE_TOPOLOGY.value),

    /** Alias of [VK_DYNAMIC_STATE_VIEWPORT_WITH_COUNT]; both names carry the same value. */
    VK_DYNAMIC_STATE_VIEWPORT_WITH_COUNT_EXT(VK_DYNAMIC_STATE_VIEWPORT_WITH_COUNT.value),

    /** Alias of [VK_DYNAMIC_STATE_SCISSOR_WITH_COUNT]; both names carry the same value. */
    VK_DYNAMIC_STATE_SCISSOR_WITH_COUNT_EXT(VK_DYNAMIC_STATE_SCISSOR_WITH_COUNT.value),

    /** Alias of [VK_DYNAMIC_STATE_VERTEX_INPUT_BINDING_STRIDE]; both names carry the same value. */
    VK_DYNAMIC_STATE_VERTEX_INPUT_BINDING_STRIDE_EXT(VK_DYNAMIC_STATE_VERTEX_INPUT_BINDING_STRIDE.value),

    /** Alias of [VK_DYNAMIC_STATE_DEPTH_TEST_ENABLE]; both names carry the same value. */
    VK_DYNAMIC_STATE_DEPTH_TEST_ENABLE_EXT(VK_DYNAMIC_STATE_DEPTH_TEST_ENABLE.value),

    /** Alias of [VK_DYNAMIC_STATE_DEPTH_WRITE_ENABLE]; both names carry the same value. */
    VK_DYNAMIC_STATE_DEPTH_WRITE_ENABLE_EXT(VK_DYNAMIC_STATE_DEPTH_WRITE_ENABLE.value),

    /** Alias of [VK_DYNAMIC_STATE_DEPTH_COMPARE_OP]; both names carry the same value. */
    VK_DYNAMIC_STATE_DEPTH_COMPARE_OP_EXT(VK_DYNAMIC_STATE_DEPTH_COMPARE_OP.value),

    /** Alias of [VK_DYNAMIC_STATE_DEPTH_BOUNDS_TEST_ENABLE]; both names carry the same value. */
    VK_DYNAMIC_STATE_DEPTH_BOUNDS_TEST_ENABLE_EXT(VK_DYNAMIC_STATE_DEPTH_BOUNDS_TEST_ENABLE.value),

    /** Alias of [VK_DYNAMIC_STATE_STENCIL_TEST_ENABLE]; both names carry the same value. */
    VK_DYNAMIC_STATE_STENCIL_TEST_ENABLE_EXT(VK_DYNAMIC_STATE_STENCIL_TEST_ENABLE.value),

    /** Alias of [VK_DYNAMIC_STATE_STENCIL_OP]; both names carry the same value. */
    VK_DYNAMIC_STATE_STENCIL_OP_EXT(VK_DYNAMIC_STATE_STENCIL_OP.value),

    /** Alias of [VK_DYNAMIC_STATE_RASTERIZER_DISCARD_ENABLE]; both names carry the same value. */
    VK_DYNAMIC_STATE_RASTERIZER_DISCARD_ENABLE_EXT(VK_DYNAMIC_STATE_RASTERIZER_DISCARD_ENABLE.value),

    /** Alias of [VK_DYNAMIC_STATE_DEPTH_BIAS_ENABLE]; both names carry the same value. */
    VK_DYNAMIC_STATE_DEPTH_BIAS_ENABLE_EXT(VK_DYNAMIC_STATE_DEPTH_BIAS_ENABLE.value),

    /** Alias of [VK_DYNAMIC_STATE_PRIMITIVE_RESTART_ENABLE]; both names carry the same value. */
    VK_DYNAMIC_STATE_PRIMITIVE_RESTART_ENABLE_EXT(VK_DYNAMIC_STATE_PRIMITIVE_RESTART_ENABLE.value),

    /** Sentinel that forces the C enum to 32 bits; it is never a valid value. */
    VK_DYNAMIC_STATE_MAX_ENUM(0x7FFFFFFF),
}

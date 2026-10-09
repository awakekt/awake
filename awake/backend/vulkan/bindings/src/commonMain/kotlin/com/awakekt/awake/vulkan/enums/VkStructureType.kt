/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums

/**
 * The `sType` tag that every extensible Vulkan structure starts with, so the driver can tell which
 * structure a pointer refers to (`VkStructureType`). Each constant is `VK_STRUCTURE_TYPE_` followed
 * by the structure's name in upper snake case.
 *
 * @property value The raw integer value Vulkan uses for this tag.
 */
enum class VkStructureType(val value: Int) {
    /** `sType` tag for the application info structure. */
    VK_STRUCTURE_TYPE_APPLICATION_INFO(0),

    /** `sType` tag for the instance create info structure. */
    VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO(1),

    /** `sType` tag for the device queue create info structure. */
    VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO(2),

    /** `sType` tag for the device create info structure. */
    VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO(3),

    /** `sType` tag for the submit info structure. */
    VK_STRUCTURE_TYPE_SUBMIT_INFO(4),

    /** `sType` tag for the memory allocate info structure. */
    VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO(5),

    /** `sType` tag for the mapped memory range structure. */
    VK_STRUCTURE_TYPE_MAPPED_MEMORY_RANGE(6),

    /** `sType` tag for the bind sparse info structure. */
    VK_STRUCTURE_TYPE_BIND_SPARSE_INFO(7),

    /** `sType` tag for the fence create info structure. */
    VK_STRUCTURE_TYPE_FENCE_CREATE_INFO(8),

    /** `sType` tag for the semaphore create info structure. */
    VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO(9),

    /** `sType` tag for the event create info structure. */
    VK_STRUCTURE_TYPE_EVENT_CREATE_INFO(10),

    /** `sType` tag for the query pool create info structure. */
    VK_STRUCTURE_TYPE_QUERY_POOL_CREATE_INFO(11),

    /** `sType` tag for the buffer create info structure. */
    VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO(12),

    /** `sType` tag for the buffer view create info structure. */
    VK_STRUCTURE_TYPE_BUFFER_VIEW_CREATE_INFO(13),

    /** `sType` tag for the image create info structure. */
    VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO(14),

    /** `sType` tag for the image view create info structure. */
    VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO(15),

    /** `sType` tag for the shader module create info structure. */
    VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO(16),

    /** `sType` tag for the pipeline cache create info structure. */
    VK_STRUCTURE_TYPE_PIPELINE_CACHE_CREATE_INFO(17),

    /** `sType` tag for the pipeline shader stage create info structure. */
    VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO(18),

    /** `sType` tag for the pipeline vertex input state create info structure. */
    VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO(19),

    /** `sType` tag for the pipeline input assembly state create info structure. */
    VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO(20),

    /** `sType` tag for the pipeline tessellation state create info structure. */
    VK_STRUCTURE_TYPE_PIPELINE_TESSELLATION_STATE_CREATE_INFO(21),

    /** `sType` tag for the pipeline viewport state create info structure. */
    VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO(22),

    /** `sType` tag for the pipeline rasterization state create info structure. */
    VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO(23),

    /** `sType` tag for the pipeline multisample state create info structure. */
    VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO(24),

    /** `sType` tag for the pipeline depth stencil state create info structure. */
    VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO(25),

    /** `sType` tag for the pipeline color blend state create info structure. */
    VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO(26),

    /** `sType` tag for the pipeline dynamic state create info structure. */
    VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO(27),

    /** `sType` tag for the graphics pipeline create info structure. */
    VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO(28),

    /** `sType` tag for the compute pipeline create info structure. */
    VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO(29),

    /** `sType` tag for the pipeline layout create info structure. */
    VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO(30),

    /** `sType` tag for the sampler create info structure. */
    VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO(31),

    /** `sType` tag for the descriptor set layout create info structure. */
    VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO(32),

    /** `sType` tag for the descriptor pool create info structure. */
    VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO(33),

    /** `sType` tag for the descriptor set allocate info structure. */
    VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO(34),

    /** `sType` tag for the write descriptor set structure. */
    VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET(35),

    /** `sType` tag for the copy descriptor set structure. */
    VK_STRUCTURE_TYPE_COPY_DESCRIPTOR_SET(36),

    /** `sType` tag for the framebuffer create info structure. */
    VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO(37),

    /** `sType` tag for the render pass create info structure. */
    VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO(38),

    /** `sType` tag for the command pool create info structure. */
    VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO(39),

    /** `sType` tag for the command buffer allocate info structure. */
    VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO(40),

    /** `sType` tag for the command buffer inheritance info structure. */
    VK_STRUCTURE_TYPE_COMMAND_BUFFER_INHERITANCE_INFO(41),

    /** `sType` tag for the command buffer begin info structure. */
    VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO(42),

    /** `sType` tag for the render pass begin info structure. */
    VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO(43),

    /** `sType` tag for the buffer memory barrier structure. */
    VK_STRUCTURE_TYPE_BUFFER_MEMORY_BARRIER(44),

    /** `sType` tag for the image memory barrier structure. */
    VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER(45),

    /** `sType` tag for the memory barrier structure. */
    VK_STRUCTURE_TYPE_MEMORY_BARRIER(46),

    /** `sType` tag for the loader instance create info structure. */
    VK_STRUCTURE_TYPE_LOADER_INSTANCE_CREATE_INFO(47),

    /** `sType` tag for the loader device create info structure. */
    VK_STRUCTURE_TYPE_LOADER_DEVICE_CREATE_INFO(48),

    /** `sType` tag for the physical device subgroup properties structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SUBGROUP_PROPERTIES(1000094000),

    /** `sType` tag for the bind buffer memory info structure. */
    VK_STRUCTURE_TYPE_BIND_BUFFER_MEMORY_INFO(1000157000),

    /** `sType` tag for the bind image memory info structure. */
    VK_STRUCTURE_TYPE_BIND_IMAGE_MEMORY_INFO(1000157001),

    /** `sType` tag for the physical device 16bit storage features structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_16BIT_STORAGE_FEATURES(1000083000),

    /** `sType` tag for the memory dedicated requirements structure. */
    VK_STRUCTURE_TYPE_MEMORY_DEDICATED_REQUIREMENTS(1000127000),

    /** `sType` tag for the memory dedicated allocate info structure. */
    VK_STRUCTURE_TYPE_MEMORY_DEDICATED_ALLOCATE_INFO(1000127001),

    /** `sType` tag for the memory allocate flags info structure. */
    VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_FLAGS_INFO(1000060000),

    /** `sType` tag for the device group render pass begin info structure. */
    VK_STRUCTURE_TYPE_DEVICE_GROUP_RENDER_PASS_BEGIN_INFO(1000060003),

    /** `sType` tag for the device group command buffer begin info structure. */
    VK_STRUCTURE_TYPE_DEVICE_GROUP_COMMAND_BUFFER_BEGIN_INFO(1000060004),

    /** `sType` tag for the device group submit info structure. */
    VK_STRUCTURE_TYPE_DEVICE_GROUP_SUBMIT_INFO(1000060005),

    /** `sType` tag for the device group bind sparse info structure. */
    VK_STRUCTURE_TYPE_DEVICE_GROUP_BIND_SPARSE_INFO(1000060006),

    /** `sType` tag for the bind buffer memory device group info structure. */
    VK_STRUCTURE_TYPE_BIND_BUFFER_MEMORY_DEVICE_GROUP_INFO(1000060013),

    /** `sType` tag for the bind image memory device group info structure. */
    VK_STRUCTURE_TYPE_BIND_IMAGE_MEMORY_DEVICE_GROUP_INFO(1000060014),

    /** `sType` tag for the physical device group properties structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_GROUP_PROPERTIES(1000070000),

    /** `sType` tag for the device group device create info structure. */
    VK_STRUCTURE_TYPE_DEVICE_GROUP_DEVICE_CREATE_INFO(1000070001),

    /** `sType` tag for the buffer memory requirements info 2 structure. */
    VK_STRUCTURE_TYPE_BUFFER_MEMORY_REQUIREMENTS_INFO_2(1000146000),

    /** `sType` tag for the image memory requirements info 2 structure. */
    VK_STRUCTURE_TYPE_IMAGE_MEMORY_REQUIREMENTS_INFO_2(1000146001),

    /** `sType` tag for the image sparse memory requirements info 2 structure. */
    VK_STRUCTURE_TYPE_IMAGE_SPARSE_MEMORY_REQUIREMENTS_INFO_2(1000146002),

    /** `sType` tag for the memory requirements 2 structure. */
    VK_STRUCTURE_TYPE_MEMORY_REQUIREMENTS_2(1000146003),

    /** `sType` tag for the sparse image memory requirements 2 structure. */
    VK_STRUCTURE_TYPE_SPARSE_IMAGE_MEMORY_REQUIREMENTS_2(1000146004),

    /** `sType` tag for the physical device features 2 structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2(1000059000),

    /** `sType` tag for the physical device properties 2 structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2(1000059001),

    /** `sType` tag for the format properties 2 structure. */
    VK_STRUCTURE_TYPE_FORMAT_PROPERTIES_2(1000059002),

    /** `sType` tag for the image format properties 2 structure. */
    VK_STRUCTURE_TYPE_IMAGE_FORMAT_PROPERTIES_2(1000059003),

    /** `sType` tag for the physical device image format info 2 structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_IMAGE_FORMAT_INFO_2(1000059004),

    /** `sType` tag for the queue family properties 2 structure. */
    VK_STRUCTURE_TYPE_QUEUE_FAMILY_PROPERTIES_2(1000059005),

    /** `sType` tag for the physical device memory properties 2 structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MEMORY_PROPERTIES_2(1000059006),

    /** `sType` tag for the sparse image format properties 2 structure. */
    VK_STRUCTURE_TYPE_SPARSE_IMAGE_FORMAT_PROPERTIES_2(1000059007),

    /** `sType` tag for the physical device sparse image format info 2 structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SPARSE_IMAGE_FORMAT_INFO_2(1000059008),

    /** `sType` tag for the physical device point clipping properties structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_POINT_CLIPPING_PROPERTIES(1000117000),

    /** `sType` tag for the render pass input attachment aspect create info structure. */
    VK_STRUCTURE_TYPE_RENDER_PASS_INPUT_ATTACHMENT_ASPECT_CREATE_INFO(1000117001),

    /** `sType` tag for the image view usage create info structure. */
    VK_STRUCTURE_TYPE_IMAGE_VIEW_USAGE_CREATE_INFO(1000117002),

    /** `sType` tag for the pipeline tessellation domain origin state create info structure. */
    VK_STRUCTURE_TYPE_PIPELINE_TESSELLATION_DOMAIN_ORIGIN_STATE_CREATE_INFO(1000117003),

    /** `sType` tag for the render pass multiview create info structure. */
    VK_STRUCTURE_TYPE_RENDER_PASS_MULTIVIEW_CREATE_INFO(1000053000),

    /** `sType` tag for the physical device multiview features structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MULTIVIEW_FEATURES(1000053001),

    /** `sType` tag for the physical device multiview properties structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MULTIVIEW_PROPERTIES(1000053002),

    /** `sType` tag for the physical device variable pointers features structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VARIABLE_POINTERS_FEATURES(1000120000),

    /** `sType` tag for the protected submit info structure. */
    VK_STRUCTURE_TYPE_PROTECTED_SUBMIT_INFO(1000145000),

    /** `sType` tag for the physical device protected memory features structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROTECTED_MEMORY_FEATURES(1000145001),

    /** `sType` tag for the physical device protected memory properties structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROTECTED_MEMORY_PROPERTIES(1000145002),

    /** `sType` tag for the device queue info 2 structure. */
    VK_STRUCTURE_TYPE_DEVICE_QUEUE_INFO_2(1000145003),

    /** `sType` tag for the sampler ycbcr conversion create info structure. */
    VK_STRUCTURE_TYPE_SAMPLER_YCBCR_CONVERSION_CREATE_INFO(1000156000),

    /** `sType` tag for the sampler ycbcr conversion info structure. */
    VK_STRUCTURE_TYPE_SAMPLER_YCBCR_CONVERSION_INFO(1000156001),

    /** `sType` tag for the bind image plane memory info structure. */
    VK_STRUCTURE_TYPE_BIND_IMAGE_PLANE_MEMORY_INFO(1000156002),

    /** `sType` tag for the image plane memory requirements info structure. */
    VK_STRUCTURE_TYPE_IMAGE_PLANE_MEMORY_REQUIREMENTS_INFO(1000156003),

    /** `sType` tag for the physical device sampler ycbcr conversion features structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SAMPLER_YCBCR_CONVERSION_FEATURES(1000156004),

    /** `sType` tag for the sampler ycbcr conversion image format properties structure. */
    VK_STRUCTURE_TYPE_SAMPLER_YCBCR_CONVERSION_IMAGE_FORMAT_PROPERTIES(1000156005),

    /** `sType` tag for the descriptor update template create info structure. */
    VK_STRUCTURE_TYPE_DESCRIPTOR_UPDATE_TEMPLATE_CREATE_INFO(1000085000),

    /** `sType` tag for the physical device external image format info structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_EXTERNAL_IMAGE_FORMAT_INFO(1000071000),

    /** `sType` tag for the external image format properties structure. */
    VK_STRUCTURE_TYPE_EXTERNAL_IMAGE_FORMAT_PROPERTIES(1000071001),

    /** `sType` tag for the physical device external buffer info structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_EXTERNAL_BUFFER_INFO(1000071002),

    /** `sType` tag for the external buffer properties structure. */
    VK_STRUCTURE_TYPE_EXTERNAL_BUFFER_PROPERTIES(1000071003),

    /** `sType` tag for the physical device id properties structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_ID_PROPERTIES(1000071004),

    /** `sType` tag for the external memory buffer create info structure. */
    VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_BUFFER_CREATE_INFO(1000072000),

    /** `sType` tag for the external memory image create info structure. */
    VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO(1000072001),

    /** `sType` tag for the export memory allocate info structure. */
    VK_STRUCTURE_TYPE_EXPORT_MEMORY_ALLOCATE_INFO(1000072002),

    /** `sType` tag for the physical device external fence info structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_EXTERNAL_FENCE_INFO(1000112000),

    /** `sType` tag for the external fence properties structure. */
    VK_STRUCTURE_TYPE_EXTERNAL_FENCE_PROPERTIES(1000112001),

    /** `sType` tag for the export fence create info structure. */
    VK_STRUCTURE_TYPE_EXPORT_FENCE_CREATE_INFO(1000113000),

    /** `sType` tag for the export semaphore create info structure. */
    VK_STRUCTURE_TYPE_EXPORT_SEMAPHORE_CREATE_INFO(1000077000),

    /** `sType` tag for the physical device external semaphore info structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_EXTERNAL_SEMAPHORE_INFO(1000076000),

    /** `sType` tag for the external semaphore properties structure. */
    VK_STRUCTURE_TYPE_EXTERNAL_SEMAPHORE_PROPERTIES(1000076001),

    /** `sType` tag for the physical device maintenance 3 properties structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MAINTENANCE_3_PROPERTIES(1000168000),

    /** `sType` tag for the descriptor set layout support structure. */
    VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_SUPPORT(1000168001),

    /** `sType` tag for the physical device shader draw parameters features structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_DRAW_PARAMETERS_FEATURES(1000063000),

    /** `sType` tag for the physical device vulkan 1 1 features structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_1_FEATURES(49),

    /** `sType` tag for the physical device vulkan 1 1 properties structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_1_PROPERTIES(50),

    /** `sType` tag for the physical device vulkan 1 2 features structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_2_FEATURES(51),

    /** `sType` tag for the physical device vulkan 1 2 properties structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_2_PROPERTIES(52),

    /** `sType` tag for the image format list create info structure. */
    VK_STRUCTURE_TYPE_IMAGE_FORMAT_LIST_CREATE_INFO(1000147000),

    /** `sType` tag for the attachment description 2 structure. */
    VK_STRUCTURE_TYPE_ATTACHMENT_DESCRIPTION_2(1000109000),

    /** `sType` tag for the attachment reference 2 structure. */
    VK_STRUCTURE_TYPE_ATTACHMENT_REFERENCE_2(1000109001),

    /** `sType` tag for the subpass description 2 structure. */
    VK_STRUCTURE_TYPE_SUBPASS_DESCRIPTION_2(1000109002),

    /** `sType` tag for the subpass dependency 2 structure. */
    VK_STRUCTURE_TYPE_SUBPASS_DEPENDENCY_2(1000109003),

    /** `sType` tag for the render pass create info 2 structure. */
    VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO_2(1000109004),

    /** `sType` tag for the subpass begin info structure. */
    VK_STRUCTURE_TYPE_SUBPASS_BEGIN_INFO(1000109005),

    /** `sType` tag for the subpass end info structure. */
    VK_STRUCTURE_TYPE_SUBPASS_END_INFO(1000109006),

    /** `sType` tag for the physical device 8bit storage features structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_8BIT_STORAGE_FEATURES(1000177000),

    /** `sType` tag for the physical device driver properties structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DRIVER_PROPERTIES(1000196000),

    /** `sType` tag for the physical device shader atomic int64 features structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_ATOMIC_INT64_FEATURES(1000180000),

    /** `sType` tag for the physical device shader float16 int8 features structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_FLOAT16_INT8_FEATURES(1000082000),

    /** `sType` tag for the physical device float controls properties structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FLOAT_CONTROLS_PROPERTIES(1000197000),

    /** `sType` tag for the descriptor set layout binding flags create info structure. */
    VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_BINDING_FLAGS_CREATE_INFO(1000161000),

    /** `sType` tag for the physical device descriptor indexing features structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DESCRIPTOR_INDEXING_FEATURES(1000161001),

    /** `sType` tag for the physical device descriptor indexing properties structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DESCRIPTOR_INDEXING_PROPERTIES(1000161002),

    /** `sType` tag for the descriptor set variable descriptor count allocate info structure. */
    VK_STRUCTURE_TYPE_DESCRIPTOR_SET_VARIABLE_DESCRIPTOR_COUNT_ALLOCATE_INFO(1000161003),

    /** `sType` tag for the descriptor set variable descriptor count layout support structure. */
    VK_STRUCTURE_TYPE_DESCRIPTOR_SET_VARIABLE_DESCRIPTOR_COUNT_LAYOUT_SUPPORT(1000161004),

    /** `sType` tag for the physical device depth stencil resolve properties structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DEPTH_STENCIL_RESOLVE_PROPERTIES(1000199000),

    /** `sType` tag for the subpass description depth stencil resolve structure. */
    VK_STRUCTURE_TYPE_SUBPASS_DESCRIPTION_DEPTH_STENCIL_RESOLVE(1000199001),

    /** `sType` tag for the physical device scalar block layout features structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SCALAR_BLOCK_LAYOUT_FEATURES(1000221000),

    /** `sType` tag for the image stencil usage create info structure. */
    VK_STRUCTURE_TYPE_IMAGE_STENCIL_USAGE_CREATE_INFO(1000246000),

    /** `sType` tag for the physical device sampler filter minmax properties structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SAMPLER_FILTER_MINMAX_PROPERTIES(1000130000),

    /** `sType` tag for the sampler reduction mode create info structure. */
    VK_STRUCTURE_TYPE_SAMPLER_REDUCTION_MODE_CREATE_INFO(1000130001),

    /** `sType` tag for the physical device vulkan memory model features structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_MEMORY_MODEL_FEATURES(1000211000),

    /** `sType` tag for the physical device imageless framebuffer features structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_IMAGELESS_FRAMEBUFFER_FEATURES(1000108000),

    /** `sType` tag for the framebuffer attachments create info structure. */
    VK_STRUCTURE_TYPE_FRAMEBUFFER_ATTACHMENTS_CREATE_INFO(1000108001),

    /** `sType` tag for the framebuffer attachment image info structure. */
    VK_STRUCTURE_TYPE_FRAMEBUFFER_ATTACHMENT_IMAGE_INFO(1000108002),

    /** `sType` tag for the render pass attachment begin info structure. */
    VK_STRUCTURE_TYPE_RENDER_PASS_ATTACHMENT_BEGIN_INFO(1000108003),

    /** `sType` tag for the physical device uniform buffer standard layout features structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_UNIFORM_BUFFER_STANDARD_LAYOUT_FEATURES(1000253000),

    /** `sType` tag for the physical device shader subgroup extended types features structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_SUBGROUP_EXTENDED_TYPES_FEATURES(1000175000),

    /** `sType` tag for the physical device separate depth stencil layouts features structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SEPARATE_DEPTH_STENCIL_LAYOUTS_FEATURES(1000241000),

    /** `sType` tag for the attachment reference stencil layout structure. */
    VK_STRUCTURE_TYPE_ATTACHMENT_REFERENCE_STENCIL_LAYOUT(1000241001),

    /** `sType` tag for the attachment description stencil layout structure. */
    VK_STRUCTURE_TYPE_ATTACHMENT_DESCRIPTION_STENCIL_LAYOUT(1000241002),

    /** `sType` tag for the physical device host query reset features structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_HOST_QUERY_RESET_FEATURES(1000261000),

    /** `sType` tag for the physical device timeline semaphore features structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_TIMELINE_SEMAPHORE_FEATURES(1000207000),

    /** `sType` tag for the physical device timeline semaphore properties structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_TIMELINE_SEMAPHORE_PROPERTIES(1000207001),

    /** `sType` tag for the semaphore type create info structure. */
    VK_STRUCTURE_TYPE_SEMAPHORE_TYPE_CREATE_INFO(1000207002),

    /** `sType` tag for the timeline semaphore submit info structure. */
    VK_STRUCTURE_TYPE_TIMELINE_SEMAPHORE_SUBMIT_INFO(1000207003),

    /** `sType` tag for the semaphore wait info structure. */
    VK_STRUCTURE_TYPE_SEMAPHORE_WAIT_INFO(1000207004),

    /** `sType` tag for the semaphore signal info structure. */
    VK_STRUCTURE_TYPE_SEMAPHORE_SIGNAL_INFO(1000207005),

    /** `sType` tag for the physical device buffer device address features structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_BUFFER_DEVICE_ADDRESS_FEATURES(1000257000),

    /** `sType` tag for the buffer device address info structure. */
    VK_STRUCTURE_TYPE_BUFFER_DEVICE_ADDRESS_INFO(1000244001),

    /** `sType` tag for the buffer opaque capture address create info structure. */
    VK_STRUCTURE_TYPE_BUFFER_OPAQUE_CAPTURE_ADDRESS_CREATE_INFO(1000257002),

    /** `sType` tag for the memory opaque capture address allocate info structure. */
    VK_STRUCTURE_TYPE_MEMORY_OPAQUE_CAPTURE_ADDRESS_ALLOCATE_INFO(1000257003),

    /** `sType` tag for the device memory opaque capture address info structure. */
    VK_STRUCTURE_TYPE_DEVICE_MEMORY_OPAQUE_CAPTURE_ADDRESS_INFO(1000257004),

    /** `sType` tag for the physical device vulkan 1 3 features structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_3_FEATURES(53),

    /** `sType` tag for the physical device vulkan 1 3 properties structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_3_PROPERTIES(54),

    /** `sType` tag for the pipeline creation feedback create info structure. */
    VK_STRUCTURE_TYPE_PIPELINE_CREATION_FEEDBACK_CREATE_INFO(1000192000),

    /** `sType` tag for the physical device shader terminate invocation features structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_TERMINATE_INVOCATION_FEATURES(1000215000),

    /** `sType` tag for the physical device tool properties structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_TOOL_PROPERTIES(1000245000),

    /**
     * `sType` tag for the physical device shader demote to helper invocation features structure.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_DEMOTE_TO_HELPER_INVOCATION_FEATURES(1000276000),

    /** `sType` tag for the physical device private data features structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PRIVATE_DATA_FEATURES(1000295000),

    /** `sType` tag for the device private data create info structure. */
    VK_STRUCTURE_TYPE_DEVICE_PRIVATE_DATA_CREATE_INFO(1000295001),

    /** `sType` tag for the private data slot create info structure. */
    VK_STRUCTURE_TYPE_PRIVATE_DATA_SLOT_CREATE_INFO(1000295002),

    /** `sType` tag for the physical device pipeline creation cache control features structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PIPELINE_CREATION_CACHE_CONTROL_FEATURES(1000297000),

    /** `sType` tag for the memory barrier 2 structure. */
    VK_STRUCTURE_TYPE_MEMORY_BARRIER_2(1000314000),

    /** `sType` tag for the buffer memory barrier 2 structure. */
    VK_STRUCTURE_TYPE_BUFFER_MEMORY_BARRIER_2(1000314001),

    /** `sType` tag for the image memory barrier 2 structure. */
    VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER_2(1000314002),

    /** `sType` tag for the dependency info structure. */
    VK_STRUCTURE_TYPE_DEPENDENCY_INFO(1000314003),

    /** `sType` tag for the submit info 2 structure. */
    VK_STRUCTURE_TYPE_SUBMIT_INFO_2(1000314004),

    /** `sType` tag for the semaphore submit info structure. */
    VK_STRUCTURE_TYPE_SEMAPHORE_SUBMIT_INFO(1000314005),

    /** `sType` tag for the command buffer submit info structure. */
    VK_STRUCTURE_TYPE_COMMAND_BUFFER_SUBMIT_INFO(1000314006),

    /** `sType` tag for the physical device synchronization 2 features structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SYNCHRONIZATION_2_FEATURES(1000314007),

    /** `sType` tag for the physical device zero initialize workgroup memory features structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_ZERO_INITIALIZE_WORKGROUP_MEMORY_FEATURES(1000325000),

    /** `sType` tag for the physical device image robustness features structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_IMAGE_ROBUSTNESS_FEATURES(1000335000),

    /** `sType` tag for the copy buffer info 2 structure. */
    VK_STRUCTURE_TYPE_COPY_BUFFER_INFO_2(1000337000),

    /** `sType` tag for the copy image info 2 structure. */
    VK_STRUCTURE_TYPE_COPY_IMAGE_INFO_2(1000337001),

    /** `sType` tag for the copy buffer to image info 2 structure. */
    VK_STRUCTURE_TYPE_COPY_BUFFER_TO_IMAGE_INFO_2(1000337002),

    /** `sType` tag for the copy image to buffer info 2 structure. */
    VK_STRUCTURE_TYPE_COPY_IMAGE_TO_BUFFER_INFO_2(1000337003),

    /** `sType` tag for the blit image info 2 structure. */
    VK_STRUCTURE_TYPE_BLIT_IMAGE_INFO_2(1000337004),

    /** `sType` tag for the resolve image info 2 structure. */
    VK_STRUCTURE_TYPE_RESOLVE_IMAGE_INFO_2(1000337005),

    /** `sType` tag for the buffer copy 2 structure. */
    VK_STRUCTURE_TYPE_BUFFER_COPY_2(1000337006),

    /** `sType` tag for the image copy 2 structure. */
    VK_STRUCTURE_TYPE_IMAGE_COPY_2(1000337007),

    /** `sType` tag for the image blit 2 structure. */
    VK_STRUCTURE_TYPE_IMAGE_BLIT_2(1000337008),

    /** `sType` tag for the buffer image copy 2 structure. */
    VK_STRUCTURE_TYPE_BUFFER_IMAGE_COPY_2(1000337009),

    /** `sType` tag for the image resolve 2 structure. */
    VK_STRUCTURE_TYPE_IMAGE_RESOLVE_2(1000337010),

    /** `sType` tag for the physical device subgroup size control properties structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SUBGROUP_SIZE_CONTROL_PROPERTIES(1000225000),

    /** `sType` tag for the pipeline shader stage required subgroup size create info structure. */
    VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_REQUIRED_SUBGROUP_SIZE_CREATE_INFO(1000225001),

    /** `sType` tag for the physical device subgroup size control features structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SUBGROUP_SIZE_CONTROL_FEATURES(1000225002),

    /** `sType` tag for the physical device inline uniform block features structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_INLINE_UNIFORM_BLOCK_FEATURES(1000138000),

    /** `sType` tag for the physical device inline uniform block properties structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_INLINE_UNIFORM_BLOCK_PROPERTIES(1000138001),

    /** `sType` tag for the write descriptor set inline uniform block structure. */
    VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET_INLINE_UNIFORM_BLOCK(1000138002),

    /** `sType` tag for the descriptor pool inline uniform block create info structure. */
    VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_INLINE_UNIFORM_BLOCK_CREATE_INFO(1000138003),

    /** `sType` tag for the physical device texture compression astc hdr features structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_TEXTURE_COMPRESSION_ASTC_HDR_FEATURES(1000066000),

    /** `sType` tag for the rendering info structure. */
    VK_STRUCTURE_TYPE_RENDERING_INFO(1000044000),

    /** `sType` tag for the rendering attachment info structure. */
    VK_STRUCTURE_TYPE_RENDERING_ATTACHMENT_INFO(1000044001),

    /** `sType` tag for the pipeline rendering create info structure. */
    VK_STRUCTURE_TYPE_PIPELINE_RENDERING_CREATE_INFO(1000044002),

    /** `sType` tag for the physical device dynamic rendering features structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DYNAMIC_RENDERING_FEATURES(1000044003),

    /** `sType` tag for the command buffer inheritance rendering info structure. */
    VK_STRUCTURE_TYPE_COMMAND_BUFFER_INHERITANCE_RENDERING_INFO(1000044004),

    /** `sType` tag for the physical device shader integer dot product features structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_INTEGER_DOT_PRODUCT_FEATURES(1000280000),

    /** `sType` tag for the physical device shader integer dot product properties structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_INTEGER_DOT_PRODUCT_PROPERTIES(1000280001),

    /** `sType` tag for the physical device texel buffer alignment properties structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_TEXEL_BUFFER_ALIGNMENT_PROPERTIES(1000281001),

    /** `sType` tag for the format properties 3 structure. */
    VK_STRUCTURE_TYPE_FORMAT_PROPERTIES_3(1000360000),

    /** `sType` tag for the physical device maintenance 4 features structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MAINTENANCE_4_FEATURES(1000413000),

    /** `sType` tag for the physical device maintenance 4 properties structure. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MAINTENANCE_4_PROPERTIES(1000413001),

    /** `sType` tag for the device buffer memory requirements structure. */
    VK_STRUCTURE_TYPE_DEVICE_BUFFER_MEMORY_REQUIREMENTS(1000413002),

    /** `sType` tag for the device image memory requirements structure. */
    VK_STRUCTURE_TYPE_DEVICE_IMAGE_MEMORY_REQUIREMENTS(1000413003),

    /** `sType` tag for the swapchain create info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR(1000001000),

    /** `sType` tag for the present info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_PRESENT_INFO_KHR(1000001001),

    /** `sType` tag for the device group present capabilities structure (Khronos extension). */
    VK_STRUCTURE_TYPE_DEVICE_GROUP_PRESENT_CAPABILITIES_KHR(1000060007),

    /** `sType` tag for the image swapchain create info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_IMAGE_SWAPCHAIN_CREATE_INFO_KHR(1000060008),

    /** `sType` tag for the bind image memory swapchain info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_BIND_IMAGE_MEMORY_SWAPCHAIN_INFO_KHR(1000060009),

    /** `sType` tag for the acquire next image info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_ACQUIRE_NEXT_IMAGE_INFO_KHR(1000060010),

    /** `sType` tag for the device group present info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_DEVICE_GROUP_PRESENT_INFO_KHR(1000060011),

    /** `sType` tag for the device group swapchain create info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_DEVICE_GROUP_SWAPCHAIN_CREATE_INFO_KHR(1000060012),

    /** `sType` tag for the display mode create info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_DISPLAY_MODE_CREATE_INFO_KHR(1000002000),

    /** `sType` tag for the display surface create info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_DISPLAY_SURFACE_CREATE_INFO_KHR(1000002001),

    /** `sType` tag for the display present info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_DISPLAY_PRESENT_INFO_KHR(1000003000),

    /** `sType` tag for the xlib surface create info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_XLIB_SURFACE_CREATE_INFO_KHR(1000004000),

    /** `sType` tag for the xcb surface create info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_XCB_SURFACE_CREATE_INFO_KHR(1000005000),

    /** `sType` tag for the wayland surface create info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_WAYLAND_SURFACE_CREATE_INFO_KHR(1000006000),

    /** `sType` tag for the android surface create info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR(1000008000),

    /** `sType` tag for the win32 surface create info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_WIN32_SURFACE_CREATE_INFO_KHR(1000009000),

    /** `sType` tag for the debug report callback create info structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_DEBUG_REPORT_CALLBACK_CREATE_INFO_EXT(1000011000),

    /**
     * `sType` tag for the pipeline rasterization state rasterization order structure (AMD
     * extension).
     */
    VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_RASTERIZATION_ORDER_AMD(1000018000),

    /** `sType` tag for the debug marker object name info structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_DEBUG_MARKER_OBJECT_NAME_INFO_EXT(1000022000),

    /** `sType` tag for the debug marker object tag info structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_DEBUG_MARKER_OBJECT_TAG_INFO_EXT(1000022001),

    /** `sType` tag for the debug marker marker info structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_DEBUG_MARKER_MARKER_INFO_EXT(1000022002),

    // VK_ENABLE_BETA_EXTENSIONS
    /** `sType` tag for the video profile structure (Khronos extension). */
    VK_STRUCTURE_TYPE_VIDEO_PROFILE_KHR(1000023000),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /** `sType` tag for the video capabilities structure (Khronos extension). */
    VK_STRUCTURE_TYPE_VIDEO_CAPABILITIES_KHR(1000023001),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /** `sType` tag for the video picture resource structure (Khronos extension). */
    VK_STRUCTURE_TYPE_VIDEO_PICTURE_RESOURCE_KHR(1000023002),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /** `sType` tag for the video get memory properties structure (Khronos extension). */
    VK_STRUCTURE_TYPE_VIDEO_GET_MEMORY_PROPERTIES_KHR(1000023003),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /** `sType` tag for the video bind memory structure (Khronos extension). */
    VK_STRUCTURE_TYPE_VIDEO_BIND_MEMORY_KHR(1000023004),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /** `sType` tag for the video session create info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_VIDEO_SESSION_CREATE_INFO_KHR(1000023005),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /** `sType` tag for the video session parameters create info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_VIDEO_SESSION_PARAMETERS_CREATE_INFO_KHR(1000023006),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /** `sType` tag for the video session parameters update info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_VIDEO_SESSION_PARAMETERS_UPDATE_INFO_KHR(1000023007),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /** `sType` tag for the video begin coding info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_VIDEO_BEGIN_CODING_INFO_KHR(1000023008),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /** `sType` tag for the video end coding info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_VIDEO_END_CODING_INFO_KHR(1000023009),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /** `sType` tag for the video coding control info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_VIDEO_CODING_CONTROL_INFO_KHR(1000023010),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /** `sType` tag for the video reference slot structure (Khronos extension). */
    VK_STRUCTURE_TYPE_VIDEO_REFERENCE_SLOT_KHR(1000023011),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /** `sType` tag for the video queue family properties 2 structure (Khronos extension). */
    VK_STRUCTURE_TYPE_VIDEO_QUEUE_FAMILY_PROPERTIES_2_KHR(1000023012),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /** `sType` tag for the video profiles structure (Khronos extension). */
    VK_STRUCTURE_TYPE_VIDEO_PROFILES_KHR(1000023013),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /** `sType` tag for the physical device video format info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VIDEO_FORMAT_INFO_KHR(1000023014),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /** `sType` tag for the video format properties structure (Khronos extension). */
    VK_STRUCTURE_TYPE_VIDEO_FORMAT_PROPERTIES_KHR(1000023015),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /**
     * `sType` tag for the queue family query result status properties 2 structure (Khronos
     * extension).
     */
    VK_STRUCTURE_TYPE_QUEUE_FAMILY_QUERY_RESULT_STATUS_PROPERTIES_2_KHR(1000023016),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /** `sType` tag for the video decode info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_VIDEO_DECODE_INFO_KHR(1000024000),

    //
    /** `sType` tag for the dedicated allocation image create info structure (NVIDIA extension). */
    VK_STRUCTURE_TYPE_DEDICATED_ALLOCATION_IMAGE_CREATE_INFO_NV(1000026000),

    /** `sType` tag for the dedicated allocation buffer create info structure (NVIDIA extension). */
    VK_STRUCTURE_TYPE_DEDICATED_ALLOCATION_BUFFER_CREATE_INFO_NV(1000026001),

    /**
     * `sType` tag for the dedicated allocation memory allocate info structure (NVIDIA extension).
     */
    VK_STRUCTURE_TYPE_DEDICATED_ALLOCATION_MEMORY_ALLOCATE_INFO_NV(1000026002),

    /**
     * `sType` tag for the physical device transform feedback features structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_TRANSFORM_FEEDBACK_FEATURES_EXT(1000028000),

    /**
     * `sType` tag for the physical device transform feedback properties structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_TRANSFORM_FEEDBACK_PROPERTIES_EXT(1000028001),

    /**
     * `sType` tag for the pipeline rasterization state stream create info structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_STREAM_CREATE_INFO_EXT(1000028002),

    /** `sType` tag for the cu module create info structure (NVIDIA extension). */
    VK_STRUCTURE_TYPE_CU_MODULE_CREATE_INFO_NVX(1000029000),

    /** `sType` tag for the cu function create info structure (NVIDIA extension). */
    VK_STRUCTURE_TYPE_CU_FUNCTION_CREATE_INFO_NVX(1000029001),

    /** `sType` tag for the cu launch info structure (NVIDIA extension). */
    VK_STRUCTURE_TYPE_CU_LAUNCH_INFO_NVX(1000029002),

    /** `sType` tag for the image view handle info structure (NVIDIA extension). */
    VK_STRUCTURE_TYPE_IMAGE_VIEW_HANDLE_INFO_NVX(1000030000),

    /** `sType` tag for the image view address properties structure (NVIDIA extension). */
    VK_STRUCTURE_TYPE_IMAGE_VIEW_ADDRESS_PROPERTIES_NVX(1000030001),

    // VK_ENABLE_BETA_EXTENSIONS
    /** `sType` tag for the video encode h264 capabilities structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_VIDEO_ENCODE_H264_CAPABILITIES_EXT(1000038000),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /**
     * `sType` tag for the video encode h264 session create info structure (multi-vendor extension).
     */
    VK_STRUCTURE_TYPE_VIDEO_ENCODE_H264_SESSION_CREATE_INFO_EXT(1000038001),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /**
     * `sType` tag for the video encode h264 session parameters create info structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_VIDEO_ENCODE_H264_SESSION_PARAMETERS_CREATE_INFO_EXT(1000038002),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /**
     * `sType` tag for the video encode h264 session parameters add info structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_VIDEO_ENCODE_H264_SESSION_PARAMETERS_ADD_INFO_EXT(1000038003),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /** `sType` tag for the video encode h264 vcl frame info structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_VIDEO_ENCODE_H264_VCL_FRAME_INFO_EXT(1000038004),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /** `sType` tag for the video encode h264 dpb slot info structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_VIDEO_ENCODE_H264_DPB_SLOT_INFO_EXT(1000038005),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /** `sType` tag for the video encode h264 nalu slice structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_VIDEO_ENCODE_H264_NALU_SLICE_EXT(1000038006),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /**
     * `sType` tag for the video encode h264 emit picture parameters structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_VIDEO_ENCODE_H264_EMIT_PICTURE_PARAMETERS_EXT(1000038007),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /** `sType` tag for the video encode h264 profile structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_VIDEO_ENCODE_H264_PROFILE_EXT(1000038008),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /**
     * `sType` tag for the video encode h264 rate control info structure (multi-vendor extension).
     */
    VK_STRUCTURE_TYPE_VIDEO_ENCODE_H264_RATE_CONTROL_INFO_EXT(1000038009),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /**
     * `sType` tag for the video encode h264 rate control layer info structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_VIDEO_ENCODE_H264_RATE_CONTROL_LAYER_INFO_EXT(1000038010),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /** `sType` tag for the video encode h265 capabilities structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_VIDEO_ENCODE_H265_CAPABILITIES_EXT(1000039000),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /**
     * `sType` tag for the video encode h265 session create info structure (multi-vendor extension).
     */
    VK_STRUCTURE_TYPE_VIDEO_ENCODE_H265_SESSION_CREATE_INFO_EXT(1000039001),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /**
     * `sType` tag for the video encode h265 session parameters create info structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_VIDEO_ENCODE_H265_SESSION_PARAMETERS_CREATE_INFO_EXT(1000039002),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /**
     * `sType` tag for the video encode h265 session parameters add info structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_VIDEO_ENCODE_H265_SESSION_PARAMETERS_ADD_INFO_EXT(1000039003),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /** `sType` tag for the video encode h265 vcl frame info structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_VIDEO_ENCODE_H265_VCL_FRAME_INFO_EXT(1000039004),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /** `sType` tag for the video encode h265 dpb slot info structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_VIDEO_ENCODE_H265_DPB_SLOT_INFO_EXT(1000039005),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /** `sType` tag for the video encode h265 nalu slice structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_VIDEO_ENCODE_H265_NALU_SLICE_EXT(1000039006),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /**
     * `sType` tag for the video encode h265 emit picture parameters structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_VIDEO_ENCODE_H265_EMIT_PICTURE_PARAMETERS_EXT(1000039007),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /** `sType` tag for the video encode h265 profile structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_VIDEO_ENCODE_H265_PROFILE_EXT(1000039008),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /** `sType` tag for the video encode h265 reference lists structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_VIDEO_ENCODE_H265_REFERENCE_LISTS_EXT(1000039009),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /**
     * `sType` tag for the video encode h265 rate control info structure (multi-vendor extension).
     */
    VK_STRUCTURE_TYPE_VIDEO_ENCODE_H265_RATE_CONTROL_INFO_EXT(1000039010),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /**
     * `sType` tag for the video encode h265 rate control layer info structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_VIDEO_ENCODE_H265_RATE_CONTROL_LAYER_INFO_EXT(1000039011),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /** `sType` tag for the video decode h264 capabilities structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_VIDEO_DECODE_H264_CAPABILITIES_EXT(1000040000),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /**
     * `sType` tag for the video decode h264 session create info structure (multi-vendor extension).
     */
    VK_STRUCTURE_TYPE_VIDEO_DECODE_H264_SESSION_CREATE_INFO_EXT(1000040001),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /** `sType` tag for the video decode h264 picture info structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_VIDEO_DECODE_H264_PICTURE_INFO_EXT(1000040002),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /** `sType` tag for the video decode h264 mvc structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_VIDEO_DECODE_H264_MVC_EXT(1000040003),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /** `sType` tag for the video decode h264 profile structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_VIDEO_DECODE_H264_PROFILE_EXT(1000040004),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /**
     * `sType` tag for the video decode h264 session parameters create info structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_VIDEO_DECODE_H264_SESSION_PARAMETERS_CREATE_INFO_EXT(1000040005),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /**
     * `sType` tag for the video decode h264 session parameters add info structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_VIDEO_DECODE_H264_SESSION_PARAMETERS_ADD_INFO_EXT(1000040006),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /** `sType` tag for the video decode h264 dpb slot info structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_VIDEO_DECODE_H264_DPB_SLOT_INFO_EXT(1000040007),

    //
    /** `sType` tag for the texture lod gather format properties structure (AMD extension). */
    VK_STRUCTURE_TYPE_TEXTURE_LOD_GATHER_FORMAT_PROPERTIES_AMD(1000041000),

    /**
     * `sType` tag for the rendering fragment shading rate attachment info structure (Khronos
     * extension).
     */
    VK_STRUCTURE_TYPE_RENDERING_FRAGMENT_SHADING_RATE_ATTACHMENT_INFO_KHR(1000044006),

    /**
     * `sType` tag for the rendering fragment density map attachment info structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_RENDERING_FRAGMENT_DENSITY_MAP_ATTACHMENT_INFO_EXT(1000044007),

    /** `sType` tag for the attachment sample count info structure (AMD extension). */
    VK_STRUCTURE_TYPE_ATTACHMENT_SAMPLE_COUNT_INFO_AMD(1000044008),

    /** `sType` tag for the multiview per view attributes info structure (NVIDIA extension). */
    VK_STRUCTURE_TYPE_MULTIVIEW_PER_VIEW_ATTRIBUTES_INFO_NVX(1000044009),

    /**
     * `sType` tag for the stream descriptor surface create info structure (vendor-specific
     * extension).
     */
    VK_STRUCTURE_TYPE_STREAM_DESCRIPTOR_SURFACE_CREATE_INFO_GGP(1000049000),

    /**
     * `sType` tag for the physical device corner sampled image features structure (NVIDIA
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_CORNER_SAMPLED_IMAGE_FEATURES_NV(1000050000),

    /** `sType` tag for the external memory image create info structure (NVIDIA extension). */
    VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO_NV(1000056000),

    /** `sType` tag for the export memory allocate info structure (NVIDIA extension). */
    VK_STRUCTURE_TYPE_EXPORT_MEMORY_ALLOCATE_INFO_NV(1000056001),

    /** `sType` tag for the import memory win32 handle info structure (NVIDIA extension). */
    VK_STRUCTURE_TYPE_IMPORT_MEMORY_WIN32_HANDLE_INFO_NV(1000057000),

    /** `sType` tag for the export memory win32 handle info structure (NVIDIA extension). */
    VK_STRUCTURE_TYPE_EXPORT_MEMORY_WIN32_HANDLE_INFO_NV(1000057001),

    /** `sType` tag for the win32 keyed mutex acquire release info structure (NVIDIA extension). */
    VK_STRUCTURE_TYPE_WIN32_KEYED_MUTEX_ACQUIRE_RELEASE_INFO_NV(1000058000),

    /** `sType` tag for the validation flags structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_VALIDATION_FLAGS_EXT(1000061000),

    /** `sType` tag for the vi surface create info structure (vendor-specific extension). */
    VK_STRUCTURE_TYPE_VI_SURFACE_CREATE_INFO_NN(1000062000),

    /** `sType` tag for the image view astc decode mode structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_IMAGE_VIEW_ASTC_DECODE_MODE_EXT(1000067000),

    /**
     * `sType` tag for the physical device astc decode features structure (multi-vendor extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_ASTC_DECODE_FEATURES_EXT(1000067001),

    /** `sType` tag for the import memory win32 handle info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_IMPORT_MEMORY_WIN32_HANDLE_INFO_KHR(1000073000),

    /** `sType` tag for the export memory win32 handle info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_EXPORT_MEMORY_WIN32_HANDLE_INFO_KHR(1000073001),

    /** `sType` tag for the memory win32 handle properties structure (Khronos extension). */
    VK_STRUCTURE_TYPE_MEMORY_WIN32_HANDLE_PROPERTIES_KHR(1000073002),

    /** `sType` tag for the memory get win32 handle info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_MEMORY_GET_WIN32_HANDLE_INFO_KHR(1000073003),

    /** `sType` tag for the import memory fd info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_IMPORT_MEMORY_FD_INFO_KHR(1000074000),

    /** `sType` tag for the memory fd properties structure (Khronos extension). */
    VK_STRUCTURE_TYPE_MEMORY_FD_PROPERTIES_KHR(1000074001),

    /** `sType` tag for the memory get fd info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_MEMORY_GET_FD_INFO_KHR(1000074002),

    /** `sType` tag for the win32 keyed mutex acquire release info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_WIN32_KEYED_MUTEX_ACQUIRE_RELEASE_INFO_KHR(1000075000),

    /** `sType` tag for the import semaphore win32 handle info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_IMPORT_SEMAPHORE_WIN32_HANDLE_INFO_KHR(1000078000),

    /** `sType` tag for the export semaphore win32 handle info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_EXPORT_SEMAPHORE_WIN32_HANDLE_INFO_KHR(1000078001),

    /** `sType` tag for the d3d12 fence submit info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_D3D12_FENCE_SUBMIT_INFO_KHR(1000078002),

    /** `sType` tag for the semaphore get win32 handle info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_SEMAPHORE_GET_WIN32_HANDLE_INFO_KHR(1000078003),

    /** `sType` tag for the import semaphore fd info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_IMPORT_SEMAPHORE_FD_INFO_KHR(1000079000),

    /** `sType` tag for the semaphore get fd info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_SEMAPHORE_GET_FD_INFO_KHR(1000079001),

    /**
     * `sType` tag for the physical device push descriptor properties structure (Khronos extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PUSH_DESCRIPTOR_PROPERTIES_KHR(1000080000),

    /**
     * `sType` tag for the command buffer inheritance conditional rendering info structure
     * (multi-vendor extension).
     */
    VK_STRUCTURE_TYPE_COMMAND_BUFFER_INHERITANCE_CONDITIONAL_RENDERING_INFO_EXT(1000081000),

    /**
     * `sType` tag for the physical device conditional rendering features structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_CONDITIONAL_RENDERING_FEATURES_EXT(1000081001),

    /** `sType` tag for the conditional rendering begin info structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_CONDITIONAL_RENDERING_BEGIN_INFO_EXT(1000081002),

    /** `sType` tag for the present regions structure (Khronos extension). */
    VK_STRUCTURE_TYPE_PRESENT_REGIONS_KHR(1000084000),

    /**
     * `sType` tag for the pipeline viewport w scaling state create info structure (NVIDIA
     * extension).
     */
    VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_W_SCALING_STATE_CREATE_INFO_NV(1000087000),

    /** `sType` tag for the surface capabilities 2 structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_SURFACE_CAPABILITIES_2_EXT(1000090000),

    /** `sType` tag for the display power info structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_DISPLAY_POWER_INFO_EXT(1000091000),

    /** `sType` tag for the device event info structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_DEVICE_EVENT_INFO_EXT(1000091001),

    /** `sType` tag for the display event info structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_DISPLAY_EVENT_INFO_EXT(1000091002),

    /** `sType` tag for the swapchain counter create info structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_SWAPCHAIN_COUNTER_CREATE_INFO_EXT(1000091003),

    /** `sType` tag for the present times info structure (Google extension). */
    VK_STRUCTURE_TYPE_PRESENT_TIMES_INFO_GOOGLE(1000092000),

    /**
     * `sType` tag for the physical device multiview per view attributes properties structure
     * (NVIDIA extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MULTIVIEW_PER_VIEW_ATTRIBUTES_PROPERTIES_NVX(1000097000),

    /**
     * `sType` tag for the pipeline viewport swizzle state create info structure (NVIDIA extension).
     */
    VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_SWIZZLE_STATE_CREATE_INFO_NV(1000098000),

    /**
     * `sType` tag for the physical device discard rectangle properties structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DISCARD_RECTANGLE_PROPERTIES_EXT(1000099000),

    /**
     * `sType` tag for the pipeline discard rectangle state create info structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_PIPELINE_DISCARD_RECTANGLE_STATE_CREATE_INFO_EXT(1000099001),

    /**
     * `sType` tag for the physical device conservative rasterization properties structure
     * (multi-vendor extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_CONSERVATIVE_RASTERIZATION_PROPERTIES_EXT(1000101000),

    /**
     * `sType` tag for the pipeline rasterization conservative state create info structure
     * (multi-vendor extension).
     */
    VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_CONSERVATIVE_STATE_CREATE_INFO_EXT(1000101001),

    /**
     * `sType` tag for the physical device depth clip enable features structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DEPTH_CLIP_ENABLE_FEATURES_EXT(1000102000),

    /**
     * `sType` tag for the pipeline rasterization depth clip state create info structure
     * (multi-vendor extension).
     */
    VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_DEPTH_CLIP_STATE_CREATE_INFO_EXT(1000102001),

    /** `sType` tag for the hdr metadata structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_HDR_METADATA_EXT(1000105000),

    /** `sType` tag for the shared present surface capabilities structure (Khronos extension). */
    VK_STRUCTURE_TYPE_SHARED_PRESENT_SURFACE_CAPABILITIES_KHR(1000111000),

    /** `sType` tag for the import fence win32 handle info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_IMPORT_FENCE_WIN32_HANDLE_INFO_KHR(1000114000),

    /** `sType` tag for the export fence win32 handle info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_EXPORT_FENCE_WIN32_HANDLE_INFO_KHR(1000114001),

    /** `sType` tag for the fence get win32 handle info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_FENCE_GET_WIN32_HANDLE_INFO_KHR(1000114002),

    /** `sType` tag for the import fence fd info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_IMPORT_FENCE_FD_INFO_KHR(1000115000),

    /** `sType` tag for the fence get fd info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_FENCE_GET_FD_INFO_KHR(1000115001),

    /**
     * `sType` tag for the physical device performance query features structure (Khronos extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PERFORMANCE_QUERY_FEATURES_KHR(1000116000),

    /**
     * `sType` tag for the physical device performance query properties structure (Khronos
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PERFORMANCE_QUERY_PROPERTIES_KHR(1000116001),

    /** `sType` tag for the query pool performance create info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_QUERY_POOL_PERFORMANCE_CREATE_INFO_KHR(1000116002),

    /** `sType` tag for the performance query submit info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_PERFORMANCE_QUERY_SUBMIT_INFO_KHR(1000116003),

    /** `sType` tag for the acquire profiling lock info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_ACQUIRE_PROFILING_LOCK_INFO_KHR(1000116004),

    /** `sType` tag for the performance counter structure (Khronos extension). */
    VK_STRUCTURE_TYPE_PERFORMANCE_COUNTER_KHR(1000116005),

    /** `sType` tag for the performance counter description structure (Khronos extension). */
    VK_STRUCTURE_TYPE_PERFORMANCE_COUNTER_DESCRIPTION_KHR(1000116006),

    /** `sType` tag for the physical device surface info 2 structure (Khronos extension). */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SURFACE_INFO_2_KHR(1000119000),

    /** `sType` tag for the surface capabilities 2 structure (Khronos extension). */
    VK_STRUCTURE_TYPE_SURFACE_CAPABILITIES_2_KHR(1000119001),

    /** `sType` tag for the surface format 2 structure (Khronos extension). */
    VK_STRUCTURE_TYPE_SURFACE_FORMAT_2_KHR(1000119002),

    /** `sType` tag for the display properties 2 structure (Khronos extension). */
    VK_STRUCTURE_TYPE_DISPLAY_PROPERTIES_2_KHR(1000121000),

    /** `sType` tag for the display plane properties 2 structure (Khronos extension). */
    VK_STRUCTURE_TYPE_DISPLAY_PLANE_PROPERTIES_2_KHR(1000121001),

    /** `sType` tag for the display mode properties 2 structure (Khronos extension). */
    VK_STRUCTURE_TYPE_DISPLAY_MODE_PROPERTIES_2_KHR(1000121002),

    /** `sType` tag for the display plane info 2 structure (Khronos extension). */
    VK_STRUCTURE_TYPE_DISPLAY_PLANE_INFO_2_KHR(1000121003),

    /** `sType` tag for the display plane capabilities 2 structure (Khronos extension). */
    VK_STRUCTURE_TYPE_DISPLAY_PLANE_CAPABILITIES_2_KHR(1000121004),

    /** `sType` tag for the ios surface create info structure (MoltenVK extension). */
    VK_STRUCTURE_TYPE_IOS_SURFACE_CREATE_INFO_MVK(1000122000),

    /** `sType` tag for the macos surface create info structure (MoltenVK extension). */
    VK_STRUCTURE_TYPE_MACOS_SURFACE_CREATE_INFO_MVK(1000123000),

    /** `sType` tag for the debug utils object name info structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_DEBUG_UTILS_OBJECT_NAME_INFO_EXT(1000128000),

    /** `sType` tag for the debug utils object tag info structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_DEBUG_UTILS_OBJECT_TAG_INFO_EXT(1000128001),

    /** `sType` tag for the debug utils label structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_DEBUG_UTILS_LABEL_EXT(1000128002),

    /**
     * `sType` tag for the debug utils messenger callback data structure (multi-vendor extension).
     */
    VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CALLBACK_DATA_EXT(1000128003),

    /** `sType` tag for the debug utils messenger create info structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT(1000128004),

    /** `sType` tag for the android hardware buffer usage structure (Android extension). */
    VK_STRUCTURE_TYPE_ANDROID_HARDWARE_BUFFER_USAGE_ANDROID(1000129000),

    /** `sType` tag for the android hardware buffer properties structure (Android extension). */
    VK_STRUCTURE_TYPE_ANDROID_HARDWARE_BUFFER_PROPERTIES_ANDROID(1000129001),

    /**
     * `sType` tag for the android hardware buffer format properties structure (Android extension).
     */
    VK_STRUCTURE_TYPE_ANDROID_HARDWARE_BUFFER_FORMAT_PROPERTIES_ANDROID(1000129002),

    /** `sType` tag for the import android hardware buffer info structure (Android extension). */
    VK_STRUCTURE_TYPE_IMPORT_ANDROID_HARDWARE_BUFFER_INFO_ANDROID(1000129003),

    /**
     * `sType` tag for the memory get android hardware buffer info structure (Android extension).
     */
    VK_STRUCTURE_TYPE_MEMORY_GET_ANDROID_HARDWARE_BUFFER_INFO_ANDROID(1000129004),

    /** `sType` tag for the external format structure (Android extension). */
    VK_STRUCTURE_TYPE_EXTERNAL_FORMAT_ANDROID(1000129005),

    /**
     * `sType` tag for the android hardware buffer format properties 2 structure (Android
     * extension).
     */
    VK_STRUCTURE_TYPE_ANDROID_HARDWARE_BUFFER_FORMAT_PROPERTIES_2_ANDROID(1000129006),

    /** `sType` tag for the sample locations info structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_SAMPLE_LOCATIONS_INFO_EXT(1000143000),

    /**
     * `sType` tag for the render pass sample locations begin info structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_RENDER_PASS_SAMPLE_LOCATIONS_BEGIN_INFO_EXT(1000143001),

    /**
     * `sType` tag for the pipeline sample locations state create info structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_PIPELINE_SAMPLE_LOCATIONS_STATE_CREATE_INFO_EXT(1000143002),

    /**
     * `sType` tag for the physical device sample locations properties structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SAMPLE_LOCATIONS_PROPERTIES_EXT(1000143003),

    /** `sType` tag for the multisample properties structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_MULTISAMPLE_PROPERTIES_EXT(1000143004),

    /**
     * `sType` tag for the physical device blend operation advanced features structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_BLEND_OPERATION_ADVANCED_FEATURES_EXT(1000148000),

    /**
     * `sType` tag for the physical device blend operation advanced properties structure
     * (multi-vendor extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_BLEND_OPERATION_ADVANCED_PROPERTIES_EXT(1000148001),

    /**
     * `sType` tag for the pipeline color blend advanced state create info structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_ADVANCED_STATE_CREATE_INFO_EXT(1000148002),

    /**
     * `sType` tag for the pipeline coverage to color state create info structure (NVIDIA
     * extension).
     */
    VK_STRUCTURE_TYPE_PIPELINE_COVERAGE_TO_COLOR_STATE_CREATE_INFO_NV(1000149000),

    /**
     * `sType` tag for the write descriptor set acceleration structure structure (Khronos
     * extension).
     */
    VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET_ACCELERATION_STRUCTURE_KHR(1000150007),

    /**
     * `sType` tag for the acceleration structure build geometry info structure (Khronos extension).
     */
    VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_BUILD_GEOMETRY_INFO_KHR(1000150000),

    /**
     * `sType` tag for the acceleration structure device address info structure (Khronos extension).
     */
    VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_DEVICE_ADDRESS_INFO_KHR(1000150002),

    /**
     * `sType` tag for the acceleration structure geometry aabbs data structure (Khronos extension).
     */
    VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_GEOMETRY_AABBS_DATA_KHR(1000150003),

    /**
     * `sType` tag for the acceleration structure geometry instances data structure (Khronos
     * extension).
     */
    VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_GEOMETRY_INSTANCES_DATA_KHR(1000150004),

    /**
     * `sType` tag for the acceleration structure geometry triangles data structure (Khronos
     * extension).
     */
    VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_GEOMETRY_TRIANGLES_DATA_KHR(1000150005),

    /** `sType` tag for the acceleration structure geometry structure (Khronos extension). */
    VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_GEOMETRY_KHR(1000150006),

    /** `sType` tag for the acceleration structure version info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_VERSION_INFO_KHR(1000150009),

    /** `sType` tag for the copy acceleration structure info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_COPY_ACCELERATION_STRUCTURE_INFO_KHR(1000150010),

    /**
     * `sType` tag for the copy acceleration structure to memory info structure (Khronos extension).
     */
    VK_STRUCTURE_TYPE_COPY_ACCELERATION_STRUCTURE_TO_MEMORY_INFO_KHR(1000150011),

    /**
     * `sType` tag for the copy memory to acceleration structure info structure (Khronos extension).
     */
    VK_STRUCTURE_TYPE_COPY_MEMORY_TO_ACCELERATION_STRUCTURE_INFO_KHR(1000150012),

    /**
     * `sType` tag for the physical device acceleration structure features structure (Khronos
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_ACCELERATION_STRUCTURE_FEATURES_KHR(1000150013),

    /**
     * `sType` tag for the physical device acceleration structure properties structure (Khronos
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_ACCELERATION_STRUCTURE_PROPERTIES_KHR(1000150014),

    /** `sType` tag for the acceleration structure create info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_CREATE_INFO_KHR(1000150017),

    /**
     * `sType` tag for the acceleration structure build sizes info structure (Khronos extension).
     */
    VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_BUILD_SIZES_INFO_KHR(1000150020),

    /**
     * `sType` tag for the physical device ray tracing pipeline features structure (Khronos
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_RAY_TRACING_PIPELINE_FEATURES_KHR(1000347000),

    /**
     * `sType` tag for the physical device ray tracing pipeline properties structure (Khronos
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_RAY_TRACING_PIPELINE_PROPERTIES_KHR(1000347001),

    /** `sType` tag for the ray tracing pipeline create info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_RAY_TRACING_PIPELINE_CREATE_INFO_KHR(1000150015),

    /** `sType` tag for the ray tracing shader group create info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_RAY_TRACING_SHADER_GROUP_CREATE_INFO_KHR(1000150016),

    /**
     * `sType` tag for the ray tracing pipeline interface create info structure (Khronos extension).
     */
    VK_STRUCTURE_TYPE_RAY_TRACING_PIPELINE_INTERFACE_CREATE_INFO_KHR(1000150018),

    /** `sType` tag for the physical device ray query features structure (Khronos extension). */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_RAY_QUERY_FEATURES_KHR(1000348013),

    /**
     * `sType` tag for the pipeline coverage modulation state create info structure (NVIDIA
     * extension).
     */
    VK_STRUCTURE_TYPE_PIPELINE_COVERAGE_MODULATION_STATE_CREATE_INFO_NV(1000152000),

    /**
     * `sType` tag for the physical device shader sm builtins features structure (NVIDIA extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_SM_BUILTINS_FEATURES_NV(1000154000),

    /**
     * `sType` tag for the physical device shader sm builtins properties structure (NVIDIA
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_SM_BUILTINS_PROPERTIES_NV(1000154001),

    /**
     * `sType` tag for the drm format modifier properties list structure (multi-vendor extension).
     */
    VK_STRUCTURE_TYPE_DRM_FORMAT_MODIFIER_PROPERTIES_LIST_EXT(1000158000),

    /**
     * `sType` tag for the physical device image drm format modifier info structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_IMAGE_DRM_FORMAT_MODIFIER_INFO_EXT(1000158002),

    /**
     * `sType` tag for the image drm format modifier list create info structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_IMAGE_DRM_FORMAT_MODIFIER_LIST_CREATE_INFO_EXT(1000158003),

    /**
     * `sType` tag for the image drm format modifier explicit create info structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_IMAGE_DRM_FORMAT_MODIFIER_EXPLICIT_CREATE_INFO_EXT(1000158004),

    /**
     * `sType` tag for the image drm format modifier properties structure (multi-vendor extension).
     */
    VK_STRUCTURE_TYPE_IMAGE_DRM_FORMAT_MODIFIER_PROPERTIES_EXT(1000158005),

    /**
     * `sType` tag for the drm format modifier properties list 2 structure (multi-vendor extension).
     */
    VK_STRUCTURE_TYPE_DRM_FORMAT_MODIFIER_PROPERTIES_LIST_2_EXT(1000158006),

    /** `sType` tag for the validation cache create info structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_VALIDATION_CACHE_CREATE_INFO_EXT(1000160000),

    /**
     * `sType` tag for the shader module validation cache create info structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_SHADER_MODULE_VALIDATION_CACHE_CREATE_INFO_EXT(1000160001),

    // VK_ENABLE_BETA_EXTENSIONS
    /**
     * `sType` tag for the physical device portability subset features structure (Khronos
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PORTABILITY_SUBSET_FEATURES_KHR(1000163000),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /**
     * `sType` tag for the physical device portability subset properties structure (Khronos
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PORTABILITY_SUBSET_PROPERTIES_KHR(1000163001),

    //
    /**
     * `sType` tag for the pipeline viewport shading rate image state create info structure (NVIDIA
     * extension).
     */
    VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_SHADING_RATE_IMAGE_STATE_CREATE_INFO_NV(1000164000),

    /**
     * `sType` tag for the physical device shading rate image features structure (NVIDIA extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADING_RATE_IMAGE_FEATURES_NV(1000164001),

    /**
     * `sType` tag for the physical device shading rate image properties structure (NVIDIA
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADING_RATE_IMAGE_PROPERTIES_NV(1000164002),

    /**
     * `sType` tag for the pipeline viewport coarse sample order state create info structure (NVIDIA
     * extension).
     */
    VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_COARSE_SAMPLE_ORDER_STATE_CREATE_INFO_NV(1000164005),

    /** `sType` tag for the ray tracing pipeline create info structure (NVIDIA extension). */
    VK_STRUCTURE_TYPE_RAY_TRACING_PIPELINE_CREATE_INFO_NV(1000165000),

    /** `sType` tag for the acceleration structure create info structure (NVIDIA extension). */
    VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_CREATE_INFO_NV(1000165001),

    /** `sType` tag for the geometry structure (NVIDIA extension). */
    VK_STRUCTURE_TYPE_GEOMETRY_NV(1000165003),

    /** `sType` tag for the geometry triangles structure (NVIDIA extension). */
    VK_STRUCTURE_TYPE_GEOMETRY_TRIANGLES_NV(1000165004),

    /** `sType` tag for the geometry aabb structure (NVIDIA extension). */
    VK_STRUCTURE_TYPE_GEOMETRY_AABB_NV(1000165005),

    /** `sType` tag for the bind acceleration structure memory info structure (NVIDIA extension). */
    VK_STRUCTURE_TYPE_BIND_ACCELERATION_STRUCTURE_MEMORY_INFO_NV(1000165006),

    /**
     * `sType` tag for the write descriptor set acceleration structure structure (NVIDIA extension).
     */
    VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET_ACCELERATION_STRUCTURE_NV(1000165007),

    /**
     * `sType` tag for the acceleration structure memory requirements info structure (NVIDIA
     * extension).
     */
    VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_MEMORY_REQUIREMENTS_INFO_NV(1000165008),

    /** `sType` tag for the physical device ray tracing properties structure (NVIDIA extension). */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_RAY_TRACING_PROPERTIES_NV(1000165009),

    /** `sType` tag for the ray tracing shader group create info structure (NVIDIA extension). */
    VK_STRUCTURE_TYPE_RAY_TRACING_SHADER_GROUP_CREATE_INFO_NV(1000165011),

    /** `sType` tag for the acceleration structure info structure (NVIDIA extension). */
    VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_INFO_NV(1000165012),

    /**
     * `sType` tag for the physical device representative fragment test features structure (NVIDIA
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_REPRESENTATIVE_FRAGMENT_TEST_FEATURES_NV(1000166000),

    /**
     * `sType` tag for the pipeline representative fragment test state create info structure (NVIDIA
     * extension).
     */
    VK_STRUCTURE_TYPE_PIPELINE_REPRESENTATIVE_FRAGMENT_TEST_STATE_CREATE_INFO_NV(1000166001),

    /**
     * `sType` tag for the physical device image view image format info structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_IMAGE_VIEW_IMAGE_FORMAT_INFO_EXT(1000170000),

    /**
     * `sType` tag for the filter cubic image view image format properties structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_FILTER_CUBIC_IMAGE_VIEW_IMAGE_FORMAT_PROPERTIES_EXT(1000170001),

    /** `sType` tag for the import memory host pointer info structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_IMPORT_MEMORY_HOST_POINTER_INFO_EXT(1000178000),

    /** `sType` tag for the memory host pointer properties structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_MEMORY_HOST_POINTER_PROPERTIES_EXT(1000178001),

    /**
     * `sType` tag for the physical device external memory host properties structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_EXTERNAL_MEMORY_HOST_PROPERTIES_EXT(1000178002),

    /** `sType` tag for the physical device shader clock features structure (Khronos extension). */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_CLOCK_FEATURES_KHR(1000181000),

    /** `sType` tag for the pipeline compiler control create info structure (AMD extension). */
    VK_STRUCTURE_TYPE_PIPELINE_COMPILER_CONTROL_CREATE_INFO_AMD(1000183000),

    /** `sType` tag for the calibrated timestamp info structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_CALIBRATED_TIMESTAMP_INFO_EXT(1000184000),

    /** `sType` tag for the physical device shader core properties structure (AMD extension). */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_CORE_PROPERTIES_AMD(1000185000),

    // VK_ENABLE_BETA_EXTENSIONS
    /** `sType` tag for the video decode h265 capabilities structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_VIDEO_DECODE_H265_CAPABILITIES_EXT(1000187000),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /**
     * `sType` tag for the video decode h265 session create info structure (multi-vendor extension).
     */
    VK_STRUCTURE_TYPE_VIDEO_DECODE_H265_SESSION_CREATE_INFO_EXT(1000187001),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /**
     * `sType` tag for the video decode h265 session parameters create info structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_VIDEO_DECODE_H265_SESSION_PARAMETERS_CREATE_INFO_EXT(1000187002),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /**
     * `sType` tag for the video decode h265 session parameters add info structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_VIDEO_DECODE_H265_SESSION_PARAMETERS_ADD_INFO_EXT(1000187003),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /** `sType` tag for the video decode h265 profile structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_VIDEO_DECODE_H265_PROFILE_EXT(1000187004),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /** `sType` tag for the video decode h265 picture info structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_VIDEO_DECODE_H265_PICTURE_INFO_EXT(1000187005),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /** `sType` tag for the video decode h265 dpb slot info structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_VIDEO_DECODE_H265_DPB_SLOT_INFO_EXT(1000187006),

    //
    /**
     * `sType` tag for the device queue global priority create info structure (Khronos extension).
     */
    VK_STRUCTURE_TYPE_DEVICE_QUEUE_GLOBAL_PRIORITY_CREATE_INFO_KHR(1000174000),

    /**
     * `sType` tag for the physical device global priority query features structure (Khronos
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_GLOBAL_PRIORITY_QUERY_FEATURES_KHR(1000388000),

    /**
     * `sType` tag for the queue family global priority properties structure (Khronos extension).
     */
    VK_STRUCTURE_TYPE_QUEUE_FAMILY_GLOBAL_PRIORITY_PROPERTIES_KHR(1000388001),

    /** `sType` tag for the device memory overallocation create info structure (AMD extension). */
    VK_STRUCTURE_TYPE_DEVICE_MEMORY_OVERALLOCATION_CREATE_INFO_AMD(1000189000),

    /**
     * `sType` tag for the physical device vertex attribute divisor properties structure
     * (multi-vendor extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VERTEX_ATTRIBUTE_DIVISOR_PROPERTIES_EXT(1000190000),

    /**
     * `sType` tag for the pipeline vertex input divisor state create info structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_DIVISOR_STATE_CREATE_INFO_EXT(1000190001),

    /**
     * `sType` tag for the physical device vertex attribute divisor features structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VERTEX_ATTRIBUTE_DIVISOR_FEATURES_EXT(1000190002),

    /** `sType` tag for the present frame token structure (vendor-specific extension). */
    VK_STRUCTURE_TYPE_PRESENT_FRAME_TOKEN_GGP(1000191000),

    /**
     * `sType` tag for the physical device compute shader derivatives features structure (NVIDIA
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_COMPUTE_SHADER_DERIVATIVES_FEATURES_NV(1000201000),

    /** `sType` tag for the physical device mesh shader features structure (NVIDIA extension). */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MESH_SHADER_FEATURES_NV(1000202000),

    /** `sType` tag for the physical device mesh shader properties structure (NVIDIA extension). */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MESH_SHADER_PROPERTIES_NV(1000202001),

    /**
     * `sType` tag for the physical device fragment shader barycentric features structure (NVIDIA
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FRAGMENT_SHADER_BARYCENTRIC_FEATURES_NV(1000203000),

    /**
     * `sType` tag for the physical device shader image footprint features structure (NVIDIA
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_IMAGE_FOOTPRINT_FEATURES_NV(1000204000),

    /**
     * `sType` tag for the pipeline viewport exclusive scissor state create info structure (NVIDIA
     * extension).
     */
    VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_EXCLUSIVE_SCISSOR_STATE_CREATE_INFO_NV(1000205000),

    /**
     * `sType` tag for the physical device exclusive scissor features structure (NVIDIA extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_EXCLUSIVE_SCISSOR_FEATURES_NV(1000205002),

    /** `sType` tag for the checkpoint data structure (NVIDIA extension). */
    VK_STRUCTURE_TYPE_CHECKPOINT_DATA_NV(1000206000),

    /** `sType` tag for the queue family checkpoint properties structure (NVIDIA extension). */
    VK_STRUCTURE_TYPE_QUEUE_FAMILY_CHECKPOINT_PROPERTIES_NV(1000206001),

    /**
     * `sType` tag for the physical device shader integer functions 2 features structure (Intel
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_INTEGER_FUNCTIONS_2_FEATURES_INTEL(1000209000),

    /** `sType` tag for the query pool performance query create info structure (Intel extension). */
    VK_STRUCTURE_TYPE_QUERY_POOL_PERFORMANCE_QUERY_CREATE_INFO_INTEL(1000210000),

    /** `sType` tag for the initialize performance api info structure (Intel extension). */
    VK_STRUCTURE_TYPE_INITIALIZE_PERFORMANCE_API_INFO_INTEL(1000210001),

    /** `sType` tag for the performance marker info structure (Intel extension). */
    VK_STRUCTURE_TYPE_PERFORMANCE_MARKER_INFO_INTEL(1000210002),

    /** `sType` tag for the performance stream marker info structure (Intel extension). */
    VK_STRUCTURE_TYPE_PERFORMANCE_STREAM_MARKER_INFO_INTEL(1000210003),

    /** `sType` tag for the performance override info structure (Intel extension). */
    VK_STRUCTURE_TYPE_PERFORMANCE_OVERRIDE_INFO_INTEL(1000210004),

    /** `sType` tag for the performance configuration acquire info structure (Intel extension). */
    VK_STRUCTURE_TYPE_PERFORMANCE_CONFIGURATION_ACQUIRE_INFO_INTEL(1000210005),

    /**
     * `sType` tag for the physical device pci bus info properties structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PCI_BUS_INFO_PROPERTIES_EXT(1000212000),

    /** `sType` tag for the display native hdr surface capabilities structure (AMD extension). */
    VK_STRUCTURE_TYPE_DISPLAY_NATIVE_HDR_SURFACE_CAPABILITIES_AMD(1000213000),

    /** `sType` tag for the swapchain display native hdr create info structure (AMD extension). */
    VK_STRUCTURE_TYPE_SWAPCHAIN_DISPLAY_NATIVE_HDR_CREATE_INFO_AMD(1000213001),

    /** `sType` tag for the imagepipe surface create info structure (Fuchsia extension). */
    VK_STRUCTURE_TYPE_IMAGEPIPE_SURFACE_CREATE_INFO_FUCHSIA(1000214000),

    /** `sType` tag for the metal surface create info structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_METAL_SURFACE_CREATE_INFO_EXT(1000217000),

    /**
     * `sType` tag for the physical device fragment density map features structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FRAGMENT_DENSITY_MAP_FEATURES_EXT(1000218000),

    /**
     * `sType` tag for the physical device fragment density map properties structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FRAGMENT_DENSITY_MAP_PROPERTIES_EXT(1000218001),

    /**
     * `sType` tag for the render pass fragment density map create info structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_RENDER_PASS_FRAGMENT_DENSITY_MAP_CREATE_INFO_EXT(1000218002),

    /** `sType` tag for the fragment shading rate attachment info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_FRAGMENT_SHADING_RATE_ATTACHMENT_INFO_KHR(1000226000),

    /**
     * `sType` tag for the pipeline fragment shading rate state create info structure (Khronos
     * extension).
     */
    VK_STRUCTURE_TYPE_PIPELINE_FRAGMENT_SHADING_RATE_STATE_CREATE_INFO_KHR(1000226001),

    /**
     * `sType` tag for the physical device fragment shading rate properties structure (Khronos
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FRAGMENT_SHADING_RATE_PROPERTIES_KHR(1000226002),

    /**
     * `sType` tag for the physical device fragment shading rate features structure (Khronos
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FRAGMENT_SHADING_RATE_FEATURES_KHR(1000226003),

    /** `sType` tag for the physical device fragment shading rate structure (Khronos extension). */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FRAGMENT_SHADING_RATE_KHR(1000226004),

    /** `sType` tag for the physical device shader core properties 2 structure (AMD extension). */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_CORE_PROPERTIES_2_AMD(1000227000),

    /** `sType` tag for the physical device coherent memory features structure (AMD extension). */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_COHERENT_MEMORY_FEATURES_AMD(1000229000),

    /**
     * `sType` tag for the physical device shader image atomic int64 features structure
     * (multi-vendor extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_IMAGE_ATOMIC_INT64_FEATURES_EXT(1000234000),

    /**
     * `sType` tag for the physical device memory budget properties structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MEMORY_BUDGET_PROPERTIES_EXT(1000237000),

    /**
     * `sType` tag for the physical device memory priority features structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MEMORY_PRIORITY_FEATURES_EXT(1000238000),

    /** `sType` tag for the memory priority allocate info structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_MEMORY_PRIORITY_ALLOCATE_INFO_EXT(1000238001),

    /** `sType` tag for the surface protected capabilities structure (Khronos extension). */
    VK_STRUCTURE_TYPE_SURFACE_PROTECTED_CAPABILITIES_KHR(1000239000),

    /**
     * `sType` tag for the physical device dedicated allocation image aliasing features structure
     * (NVIDIA extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DEDICATED_ALLOCATION_IMAGE_ALIASING_FEATURES_NV(1000240000),

    /**
     * `sType` tag for the physical device buffer device address features structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_BUFFER_DEVICE_ADDRESS_FEATURES_EXT(1000244000),

    /** `sType` tag for the buffer device address create info structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_BUFFER_DEVICE_ADDRESS_CREATE_INFO_EXT(1000244002),

    /** `sType` tag for the validation features structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_VALIDATION_FEATURES_EXT(1000247000),

    /** `sType` tag for the physical device present wait features structure (Khronos extension). */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PRESENT_WAIT_FEATURES_KHR(1000248000),

    /**
     * `sType` tag for the physical device cooperative matrix features structure (NVIDIA extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_COOPERATIVE_MATRIX_FEATURES_NV(1000249000),

    /** `sType` tag for the cooperative matrix properties structure (NVIDIA extension). */
    VK_STRUCTURE_TYPE_COOPERATIVE_MATRIX_PROPERTIES_NV(1000249001),

    /**
     * `sType` tag for the physical device cooperative matrix properties structure (NVIDIA
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_COOPERATIVE_MATRIX_PROPERTIES_NV(1000249002),

    /**
     * `sType` tag for the physical device coverage reduction mode features structure (NVIDIA
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_COVERAGE_REDUCTION_MODE_FEATURES_NV(1000250000),

    /**
     * `sType` tag for the pipeline coverage reduction state create info structure (NVIDIA
     * extension).
     */
    VK_STRUCTURE_TYPE_PIPELINE_COVERAGE_REDUCTION_STATE_CREATE_INFO_NV(1000250001),

    /** `sType` tag for the framebuffer mixed samples combination structure (NVIDIA extension). */
    VK_STRUCTURE_TYPE_FRAMEBUFFER_MIXED_SAMPLES_COMBINATION_NV(1000250002),

    /**
     * `sType` tag for the physical device fragment shader interlock features structure
     * (multi-vendor extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FRAGMENT_SHADER_INTERLOCK_FEATURES_EXT(1000251000),

    /**
     * `sType` tag for the physical device ycbcr image arrays features structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_YCBCR_IMAGE_ARRAYS_FEATURES_EXT(1000252000),

    /**
     * `sType` tag for the physical device provoking vertex features structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROVOKING_VERTEX_FEATURES_EXT(1000254000),

    /**
     * `sType` tag for the pipeline rasterization provoking vertex state create info structure
     * (multi-vendor extension).
     */
    VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_PROVOKING_VERTEX_STATE_CREATE_INFO_EXT(1000254001),

    /**
     * `sType` tag for the physical device provoking vertex properties structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROVOKING_VERTEX_PROPERTIES_EXT(1000254002),

    /**
     * `sType` tag for the surface full screen exclusive info structure (multi-vendor extension).
     */
    VK_STRUCTURE_TYPE_SURFACE_FULL_SCREEN_EXCLUSIVE_INFO_EXT(1000255000),

    /**
     * `sType` tag for the surface capabilities full screen exclusive structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_SURFACE_CAPABILITIES_FULL_SCREEN_EXCLUSIVE_EXT(1000255002),

    /**
     * `sType` tag for the surface full screen exclusive win32 info structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_SURFACE_FULL_SCREEN_EXCLUSIVE_WIN32_INFO_EXT(1000255001),

    /** `sType` tag for the headless surface create info structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_HEADLESS_SURFACE_CREATE_INFO_EXT(1000256000),

    /**
     * `sType` tag for the physical device line rasterization features structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_LINE_RASTERIZATION_FEATURES_EXT(1000259000),

    /**
     * `sType` tag for the pipeline rasterization line state create info structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_LINE_STATE_CREATE_INFO_EXT(1000259001),

    /**
     * `sType` tag for the physical device line rasterization properties structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_LINE_RASTERIZATION_PROPERTIES_EXT(1000259002),

    /**
     * `sType` tag for the physical device shader atomic float features structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_ATOMIC_FLOAT_FEATURES_EXT(1000260000),

    /**
     * `sType` tag for the physical device index type uint8 features structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_INDEX_TYPE_UINT8_FEATURES_EXT(1000265000),

    /**
     * `sType` tag for the physical device extended dynamic state features structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_EXTENDED_DYNAMIC_STATE_FEATURES_EXT(1000267000),

    /**
     * `sType` tag for the physical device pipeline executable properties features structure
     * (Khronos extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PIPELINE_EXECUTABLE_PROPERTIES_FEATURES_KHR(1000269000),

    /** `sType` tag for the pipeline info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_PIPELINE_INFO_KHR(1000269001),

    /** `sType` tag for the pipeline executable properties structure (Khronos extension). */
    VK_STRUCTURE_TYPE_PIPELINE_EXECUTABLE_PROPERTIES_KHR(1000269002),

    /** `sType` tag for the pipeline executable info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_PIPELINE_EXECUTABLE_INFO_KHR(1000269003),

    /** `sType` tag for the pipeline executable statistic structure (Khronos extension). */
    VK_STRUCTURE_TYPE_PIPELINE_EXECUTABLE_STATISTIC_KHR(1000269004),

    /**
     * `sType` tag for the pipeline executable internal representation structure (Khronos
     * extension).
     */
    VK_STRUCTURE_TYPE_PIPELINE_EXECUTABLE_INTERNAL_REPRESENTATION_KHR(1000269005),

    /**
     * `sType` tag for the physical device shader atomic float 2 features structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_ATOMIC_FLOAT_2_FEATURES_EXT(1000273000),

    /**
     * `sType` tag for the physical device device generated commands properties structure (NVIDIA
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DEVICE_GENERATED_COMMANDS_PROPERTIES_NV(1000277000),

    /** `sType` tag for the graphics shader group create info structure (NVIDIA extension). */
    VK_STRUCTURE_TYPE_GRAPHICS_SHADER_GROUP_CREATE_INFO_NV(1000277001),

    /**
     * `sType` tag for the graphics pipeline shader groups create info structure (NVIDIA extension).
     */
    VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_SHADER_GROUPS_CREATE_INFO_NV(1000277002),

    /** `sType` tag for the indirect commands layout token structure (NVIDIA extension). */
    VK_STRUCTURE_TYPE_INDIRECT_COMMANDS_LAYOUT_TOKEN_NV(1000277003),

    /** `sType` tag for the indirect commands layout create info structure (NVIDIA extension). */
    VK_STRUCTURE_TYPE_INDIRECT_COMMANDS_LAYOUT_CREATE_INFO_NV(1000277004),

    /** `sType` tag for the generated commands info structure (NVIDIA extension). */
    VK_STRUCTURE_TYPE_GENERATED_COMMANDS_INFO_NV(1000277005),

    /**
     * `sType` tag for the generated commands memory requirements info structure (NVIDIA extension).
     */
    VK_STRUCTURE_TYPE_GENERATED_COMMANDS_MEMORY_REQUIREMENTS_INFO_NV(1000277006),

    /**
     * `sType` tag for the physical device device generated commands features structure (NVIDIA
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DEVICE_GENERATED_COMMANDS_FEATURES_NV(1000277007),

    /**
     * `sType` tag for the physical device inherited viewport scissor features structure (NVIDIA
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_INHERITED_VIEWPORT_SCISSOR_FEATURES_NV(1000278000),

    /**
     * `sType` tag for the command buffer inheritance viewport scissor info structure (NVIDIA
     * extension).
     */
    VK_STRUCTURE_TYPE_COMMAND_BUFFER_INHERITANCE_VIEWPORT_SCISSOR_INFO_NV(1000278001),

    /**
     * `sType` tag for the physical device texel buffer alignment features structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_TEXEL_BUFFER_ALIGNMENT_FEATURES_EXT(1000281000),

    /**
     * `sType` tag for the command buffer inheritance render pass transform info structure (Qualcomm
     * extension).
     */
    VK_STRUCTURE_TYPE_COMMAND_BUFFER_INHERITANCE_RENDER_PASS_TRANSFORM_INFO_QCOM(1000282000),

    /** `sType` tag for the render pass transform begin info structure (Qualcomm extension). */
    VK_STRUCTURE_TYPE_RENDER_PASS_TRANSFORM_BEGIN_INFO_QCOM(1000282001),

    /**
     * `sType` tag for the physical device device memory report features structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DEVICE_MEMORY_REPORT_FEATURES_EXT(1000284000),

    /**
     * `sType` tag for the device device memory report create info structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_DEVICE_DEVICE_MEMORY_REPORT_CREATE_INFO_EXT(1000284001),

    /**
     * `sType` tag for the device memory report callback data structure (multi-vendor extension).
     */
    VK_STRUCTURE_TYPE_DEVICE_MEMORY_REPORT_CALLBACK_DATA_EXT(1000284002),

    /**
     * `sType` tag for the physical device robustness 2 features structure (multi-vendor extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_ROBUSTNESS_2_FEATURES_EXT(1000286000),

    /**
     * `sType` tag for the physical device robustness 2 properties structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_ROBUSTNESS_2_PROPERTIES_EXT(1000286001),

    /**
     * `sType` tag for the sampler custom border color create info structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_SAMPLER_CUSTOM_BORDER_COLOR_CREATE_INFO_EXT(1000287000),

    /**
     * `sType` tag for the physical device custom border color properties structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_CUSTOM_BORDER_COLOR_PROPERTIES_EXT(1000287001),

    /**
     * `sType` tag for the physical device custom border color features structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_CUSTOM_BORDER_COLOR_FEATURES_EXT(1000287002),

    /** `sType` tag for the pipeline library create info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_PIPELINE_LIBRARY_CREATE_INFO_KHR(1000290000),

    /** `sType` tag for the present id structure (Khronos extension). */
    VK_STRUCTURE_TYPE_PRESENT_ID_KHR(1000294000),

    /** `sType` tag for the physical device present id features structure (Khronos extension). */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PRESENT_ID_FEATURES_KHR(1000294001),

    // VK_ENABLE_BETA_EXTENSIONS
    /** `sType` tag for the video encode info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_VIDEO_ENCODE_INFO_KHR(1000299000),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /** `sType` tag for the video encode rate control info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_VIDEO_ENCODE_RATE_CONTROL_INFO_KHR(1000299001),

    //
    // VK_ENABLE_BETA_EXTENSIONS
    /** `sType` tag for the video encode rate control layer info structure (Khronos extension). */
    VK_STRUCTURE_TYPE_VIDEO_ENCODE_RATE_CONTROL_LAYER_INFO_KHR(1000299002),

    //
    /**
     * `sType` tag for the physical device diagnostics config features structure (NVIDIA extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DIAGNOSTICS_CONFIG_FEATURES_NV(1000300000),

    /** `sType` tag for the device diagnostics config create info structure (NVIDIA extension). */
    VK_STRUCTURE_TYPE_DEVICE_DIAGNOSTICS_CONFIG_CREATE_INFO_NV(1000300001),

    /** `sType` tag for the queue family checkpoint properties 2 structure (NVIDIA extension). */
    VK_STRUCTURE_TYPE_QUEUE_FAMILY_CHECKPOINT_PROPERTIES_2_NV(1000314008),

    /** `sType` tag for the checkpoint data 2 structure (NVIDIA extension). */
    VK_STRUCTURE_TYPE_CHECKPOINT_DATA_2_NV(1000314009),

    /**
     * `sType` tag for the physical device shader subgroup uniform control flow features structure
     * (Khronos extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_SUBGROUP_UNIFORM_CONTROL_FLOW_FEATURES_KHR(1000323000),

    /**
     * `sType` tag for the physical device fragment shading rate enums properties structure (NVIDIA
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FRAGMENT_SHADING_RATE_ENUMS_PROPERTIES_NV(1000326000),

    /**
     * `sType` tag for the physical device fragment shading rate enums features structure (NVIDIA
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FRAGMENT_SHADING_RATE_ENUMS_FEATURES_NV(1000326001),

    /**
     * `sType` tag for the pipeline fragment shading rate enum state create info structure (NVIDIA
     * extension).
     */
    VK_STRUCTURE_TYPE_PIPELINE_FRAGMENT_SHADING_RATE_ENUM_STATE_CREATE_INFO_NV(1000326002),

    /**
     * `sType` tag for the acceleration structure geometry motion triangles data structure (NVIDIA
     * extension).
     */
    VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_GEOMETRY_MOTION_TRIANGLES_DATA_NV(1000327000),

    /**
     * `sType` tag for the physical device ray tracing motion blur features structure (NVIDIA
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_RAY_TRACING_MOTION_BLUR_FEATURES_NV(1000327001),

    /** `sType` tag for the acceleration structure motion info structure (NVIDIA extension). */
    VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_MOTION_INFO_NV(1000327002),

    /**
     * `sType` tag for the physical device ycbcr 2 plane 444 formats features structure
     * (multi-vendor extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_YCBCR_2_PLANE_444_FORMATS_FEATURES_EXT(1000330000),

    /**
     * `sType` tag for the physical device fragment density map 2 features structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FRAGMENT_DENSITY_MAP_2_FEATURES_EXT(1000332000),

    /**
     * `sType` tag for the physical device fragment density map 2 properties structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FRAGMENT_DENSITY_MAP_2_PROPERTIES_EXT(1000332001),

    /** `sType` tag for the copy command transform info structure (Qualcomm extension). */
    VK_STRUCTURE_TYPE_COPY_COMMAND_TRANSFORM_INFO_QCOM(1000333000),

    /**
     * `sType` tag for the physical device workgroup memory explicit layout features structure
     * (Khronos extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_WORKGROUP_MEMORY_EXPLICIT_LAYOUT_FEATURES_KHR(1000336000),

    /**
     * `sType` tag for the physical device 4444 formats features structure (multi-vendor extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_4444_FORMATS_FEATURES_EXT(1000340000),

    /**
     * `sType` tag for the physical device rasterization order attachment access features structure
     * (Arm extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_RASTERIZATION_ORDER_ATTACHMENT_ACCESS_FEATURES_ARM(1000342000),

    /**
     * `sType` tag for the physical device rgba10x6 formats features structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_RGBA10X6_FORMATS_FEATURES_EXT(1000344000),

    /** `sType` tag for the directfb surface create info structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_DIRECTFB_SURFACE_CREATE_INFO_EXT(1000346000),

    /**
     * `sType` tag for the physical device mutable descriptor type features structure
     * (vendor-specific extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MUTABLE_DESCRIPTOR_TYPE_FEATURES_VALVE(1000351000),

    /**
     * `sType` tag for the mutable descriptor type create info structure (vendor-specific
     * extension).
     */
    VK_STRUCTURE_TYPE_MUTABLE_DESCRIPTOR_TYPE_CREATE_INFO_VALVE(1000351002),

    /**
     * `sType` tag for the physical device vertex input dynamic state features structure
     * (multi-vendor extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VERTEX_INPUT_DYNAMIC_STATE_FEATURES_EXT(1000352000),

    /**
     * `sType` tag for the vertex input binding description 2 structure (multi-vendor extension).
     */
    VK_STRUCTURE_TYPE_VERTEX_INPUT_BINDING_DESCRIPTION_2_EXT(1000352001),

    /**
     * `sType` tag for the vertex input attribute description 2 structure (multi-vendor extension).
     */
    VK_STRUCTURE_TYPE_VERTEX_INPUT_ATTRIBUTE_DESCRIPTION_2_EXT(1000352002),

    /** `sType` tag for the physical device drm properties structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DRM_PROPERTIES_EXT(1000353000),

    /**
     * `sType` tag for the physical device depth clip control features structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DEPTH_CLIP_CONTROL_FEATURES_EXT(1000355000),

    /**
     * `sType` tag for the pipeline viewport depth clip control create info structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_DEPTH_CLIP_CONTROL_CREATE_INFO_EXT(1000355001),

    /**
     * `sType` tag for the physical device primitive topology list restart features structure
     * (multi-vendor extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PRIMITIVE_TOPOLOGY_LIST_RESTART_FEATURES_EXT(1000356000),

    /** `sType` tag for the import memory zircon handle info structure (Fuchsia extension). */
    VK_STRUCTURE_TYPE_IMPORT_MEMORY_ZIRCON_HANDLE_INFO_FUCHSIA(1000364000),

    /** `sType` tag for the memory zircon handle properties structure (Fuchsia extension). */
    VK_STRUCTURE_TYPE_MEMORY_ZIRCON_HANDLE_PROPERTIES_FUCHSIA(1000364001),

    /** `sType` tag for the memory get zircon handle info structure (Fuchsia extension). */
    VK_STRUCTURE_TYPE_MEMORY_GET_ZIRCON_HANDLE_INFO_FUCHSIA(1000364002),

    /** `sType` tag for the import semaphore zircon handle info structure (Fuchsia extension). */
    VK_STRUCTURE_TYPE_IMPORT_SEMAPHORE_ZIRCON_HANDLE_INFO_FUCHSIA(1000365000),

    /** `sType` tag for the semaphore get zircon handle info structure (Fuchsia extension). */
    VK_STRUCTURE_TYPE_SEMAPHORE_GET_ZIRCON_HANDLE_INFO_FUCHSIA(1000365001),

    /** `sType` tag for the buffer collection create info structure (Fuchsia extension). */
    VK_STRUCTURE_TYPE_BUFFER_COLLECTION_CREATE_INFO_FUCHSIA(1000366000),

    /** `sType` tag for the import memory buffer collection structure (Fuchsia extension). */
    VK_STRUCTURE_TYPE_IMPORT_MEMORY_BUFFER_COLLECTION_FUCHSIA(1000366001),

    /** `sType` tag for the buffer collection image create info structure (Fuchsia extension). */
    VK_STRUCTURE_TYPE_BUFFER_COLLECTION_IMAGE_CREATE_INFO_FUCHSIA(1000366002),

    /** `sType` tag for the buffer collection properties structure (Fuchsia extension). */
    VK_STRUCTURE_TYPE_BUFFER_COLLECTION_PROPERTIES_FUCHSIA(1000366003),

    /** `sType` tag for the buffer constraints info structure (Fuchsia extension). */
    VK_STRUCTURE_TYPE_BUFFER_CONSTRAINTS_INFO_FUCHSIA(1000366004),

    /** `sType` tag for the buffer collection buffer create info structure (Fuchsia extension). */
    VK_STRUCTURE_TYPE_BUFFER_COLLECTION_BUFFER_CREATE_INFO_FUCHSIA(1000366005),

    /** `sType` tag for the image constraints info structure (Fuchsia extension). */
    VK_STRUCTURE_TYPE_IMAGE_CONSTRAINTS_INFO_FUCHSIA(1000366006),

    /** `sType` tag for the image format constraints info structure (Fuchsia extension). */
    VK_STRUCTURE_TYPE_IMAGE_FORMAT_CONSTRAINTS_INFO_FUCHSIA(1000366007),

    /** `sType` tag for the sysmem color space structure (Fuchsia extension). */
    VK_STRUCTURE_TYPE_SYSMEM_COLOR_SPACE_FUCHSIA(1000366008),

    /** `sType` tag for the buffer collection constraints info structure (Fuchsia extension). */
    VK_STRUCTURE_TYPE_BUFFER_COLLECTION_CONSTRAINTS_INFO_FUCHSIA(1000366009),

    /** `sType` tag for the subpass shading pipeline create info structure (Huawei extension). */
    VK_STRUCTURE_TYPE_SUBPASS_SHADING_PIPELINE_CREATE_INFO_HUAWEI(1000369000),

    /**
     * `sType` tag for the physical device subpass shading features structure (Huawei extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SUBPASS_SHADING_FEATURES_HUAWEI(1000369001),

    /**
     * `sType` tag for the physical device subpass shading properties structure (Huawei extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SUBPASS_SHADING_PROPERTIES_HUAWEI(1000369002),

    /**
     * `sType` tag for the physical device invocation mask features structure (Huawei extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_INVOCATION_MASK_FEATURES_HUAWEI(1000370000),

    /** `sType` tag for the memory get remote address info structure (NVIDIA extension). */
    VK_STRUCTURE_TYPE_MEMORY_GET_REMOTE_ADDRESS_INFO_NV(1000371000),

    /**
     * `sType` tag for the physical device external memory rdma features structure (NVIDIA
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_EXTERNAL_MEMORY_RDMA_FEATURES_NV(1000371001),

    /**
     * `sType` tag for the physical device extended dynamic state 2 features structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_EXTENDED_DYNAMIC_STATE_2_FEATURES_EXT(1000377000),

    /** `sType` tag for the screen surface create info structure (QNX extension). */
    VK_STRUCTURE_TYPE_SCREEN_SURFACE_CREATE_INFO_QNX(1000378000),

    /**
     * `sType` tag for the physical device color write enable features structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_COLOR_WRITE_ENABLE_FEATURES_EXT(1000381000),

    /** `sType` tag for the pipeline color write create info structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_PIPELINE_COLOR_WRITE_CREATE_INFO_EXT(1000381001),

    /**
     * `sType` tag for the physical device image view min lod features structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_IMAGE_VIEW_MIN_LOD_FEATURES_EXT(1000391000),

    /** `sType` tag for the image view min lod create info structure (multi-vendor extension). */
    VK_STRUCTURE_TYPE_IMAGE_VIEW_MIN_LOD_CREATE_INFO_EXT(1000391001),

    /**
     * `sType` tag for the physical device multi draw features structure (multi-vendor extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MULTI_DRAW_FEATURES_EXT(1000392000),

    /**
     * `sType` tag for the physical device multi draw properties structure (multi-vendor extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MULTI_DRAW_PROPERTIES_EXT(1000392001),

    /**
     * `sType` tag for the physical device border color swizzle features structure (multi-vendor
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_BORDER_COLOR_SWIZZLE_FEATURES_EXT(1000411000),

    /**
     * `sType` tag for the sampler border color component mapping create info structure
     * (multi-vendor extension).
     */
    VK_STRUCTURE_TYPE_SAMPLER_BORDER_COLOR_COMPONENT_MAPPING_CREATE_INFO_EXT(1000411001),

    /**
     * `sType` tag for the physical device pageable device local memory features structure
     * (multi-vendor extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PAGEABLE_DEVICE_LOCAL_MEMORY_FEATURES_EXT(1000412000),

    /**
     * `sType` tag for the physical device fragment density map offset features structure (Qualcomm
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FRAGMENT_DENSITY_MAP_OFFSET_FEATURES_QCOM(1000425000),

    /**
     * `sType` tag for the physical device fragment density map offset properties structure
     * (Qualcomm extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FRAGMENT_DENSITY_MAP_OFFSET_PROPERTIES_QCOM(1000425001),

    /**
     * `sType` tag for the subpass fragment density map offset end info structure (Qualcomm
     * extension).
     */
    VK_STRUCTURE_TYPE_SUBPASS_FRAGMENT_DENSITY_MAP_OFFSET_END_INFO_QCOM(1000425002),

    /**
     * `sType` tag for the physical device linear color attachment features structure (NVIDIA
     * extension).
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_LINEAR_COLOR_ATTACHMENT_FEATURES_NV(1000430000),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VARIABLE_POINTERS_FEATURES]; both names carry the
     * same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VARIABLE_POINTER_FEATURES(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VARIABLE_POINTERS_FEATURES.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_DRAW_PARAMETERS_FEATURES]; both names
     * carry the same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_DRAW_PARAMETER_FEATURES(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_DRAW_PARAMETERS_FEATURES.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_DEBUG_REPORT_CALLBACK_CREATE_INFO_EXT]; both names carry the same
     * value.
     */
    VK_STRUCTURE_TYPE_DEBUG_REPORT_CREATE_INFO_EXT(
        VK_STRUCTURE_TYPE_DEBUG_REPORT_CALLBACK_CREATE_INFO_EXT.value,
    ),

    /** Alias of [VK_STRUCTURE_TYPE_RENDERING_INFO]; both names carry the same value. */
    VK_STRUCTURE_TYPE_RENDERING_INFO_KHR(VK_STRUCTURE_TYPE_RENDERING_INFO.value),

    /** Alias of [VK_STRUCTURE_TYPE_RENDERING_ATTACHMENT_INFO]; both names carry the same value. */
    VK_STRUCTURE_TYPE_RENDERING_ATTACHMENT_INFO_KHR(VK_STRUCTURE_TYPE_RENDERING_ATTACHMENT_INFO.value),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PIPELINE_RENDERING_CREATE_INFO]; both names carry the same value.
     */
    VK_STRUCTURE_TYPE_PIPELINE_RENDERING_CREATE_INFO_KHR(
        VK_STRUCTURE_TYPE_PIPELINE_RENDERING_CREATE_INFO.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DYNAMIC_RENDERING_FEATURES]; both names carry the
     * same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DYNAMIC_RENDERING_FEATURES_KHR(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DYNAMIC_RENDERING_FEATURES.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_COMMAND_BUFFER_INHERITANCE_RENDERING_INFO]; both names carry the
     * same value.
     */
    VK_STRUCTURE_TYPE_COMMAND_BUFFER_INHERITANCE_RENDERING_INFO_KHR(
        VK_STRUCTURE_TYPE_COMMAND_BUFFER_INHERITANCE_RENDERING_INFO.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_ATTACHMENT_SAMPLE_COUNT_INFO_AMD]; both names carry the same
     * value.
     */
    VK_STRUCTURE_TYPE_ATTACHMENT_SAMPLE_COUNT_INFO_NV(
        VK_STRUCTURE_TYPE_ATTACHMENT_SAMPLE_COUNT_INFO_AMD.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_RENDER_PASS_MULTIVIEW_CREATE_INFO]; both names carry the same
     * value.
     */
    VK_STRUCTURE_TYPE_RENDER_PASS_MULTIVIEW_CREATE_INFO_KHR(
        VK_STRUCTURE_TYPE_RENDER_PASS_MULTIVIEW_CREATE_INFO.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MULTIVIEW_FEATURES]; both names carry the same
     * value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MULTIVIEW_FEATURES_KHR(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MULTIVIEW_FEATURES.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MULTIVIEW_PROPERTIES]; both names carry the same
     * value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MULTIVIEW_PROPERTIES_KHR(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MULTIVIEW_PROPERTIES.value,
    ),

    /** Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2]; both names carry the same value. */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2_KHR(VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2.value),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2]; both names carry the same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2_KHR(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2.value,
    ),

    /** Alias of [VK_STRUCTURE_TYPE_FORMAT_PROPERTIES_2]; both names carry the same value. */
    VK_STRUCTURE_TYPE_FORMAT_PROPERTIES_2_KHR(VK_STRUCTURE_TYPE_FORMAT_PROPERTIES_2.value),

    /** Alias of [VK_STRUCTURE_TYPE_IMAGE_FORMAT_PROPERTIES_2]; both names carry the same value. */
    VK_STRUCTURE_TYPE_IMAGE_FORMAT_PROPERTIES_2_KHR(VK_STRUCTURE_TYPE_IMAGE_FORMAT_PROPERTIES_2.value),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_IMAGE_FORMAT_INFO_2]; both names carry the same
     * value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_IMAGE_FORMAT_INFO_2_KHR(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_IMAGE_FORMAT_INFO_2.value,
    ),

    /** Alias of [VK_STRUCTURE_TYPE_QUEUE_FAMILY_PROPERTIES_2]; both names carry the same value. */
    VK_STRUCTURE_TYPE_QUEUE_FAMILY_PROPERTIES_2_KHR(VK_STRUCTURE_TYPE_QUEUE_FAMILY_PROPERTIES_2.value),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MEMORY_PROPERTIES_2]; both names carry the same
     * value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MEMORY_PROPERTIES_2_KHR(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MEMORY_PROPERTIES_2.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_SPARSE_IMAGE_FORMAT_PROPERTIES_2]; both names carry the same
     * value.
     */
    VK_STRUCTURE_TYPE_SPARSE_IMAGE_FORMAT_PROPERTIES_2_KHR(
        VK_STRUCTURE_TYPE_SPARSE_IMAGE_FORMAT_PROPERTIES_2.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SPARSE_IMAGE_FORMAT_INFO_2]; both names carry the
     * same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SPARSE_IMAGE_FORMAT_INFO_2_KHR(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SPARSE_IMAGE_FORMAT_INFO_2.value,
    ),

    /** Alias of [VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_FLAGS_INFO]; both names carry the same value. */
    VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_FLAGS_INFO_KHR(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_FLAGS_INFO.value),

    /**
     * Alias of [VK_STRUCTURE_TYPE_DEVICE_GROUP_RENDER_PASS_BEGIN_INFO]; both names carry the same
     * value.
     */
    VK_STRUCTURE_TYPE_DEVICE_GROUP_RENDER_PASS_BEGIN_INFO_KHR(
        VK_STRUCTURE_TYPE_DEVICE_GROUP_RENDER_PASS_BEGIN_INFO.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_DEVICE_GROUP_COMMAND_BUFFER_BEGIN_INFO]; both names carry the
     * same value.
     */
    VK_STRUCTURE_TYPE_DEVICE_GROUP_COMMAND_BUFFER_BEGIN_INFO_KHR(
        VK_STRUCTURE_TYPE_DEVICE_GROUP_COMMAND_BUFFER_BEGIN_INFO.value,
    ),

    /** Alias of [VK_STRUCTURE_TYPE_DEVICE_GROUP_SUBMIT_INFO]; both names carry the same value. */
    VK_STRUCTURE_TYPE_DEVICE_GROUP_SUBMIT_INFO_KHR(VK_STRUCTURE_TYPE_DEVICE_GROUP_SUBMIT_INFO.value),

    /**
     * Alias of [VK_STRUCTURE_TYPE_DEVICE_GROUP_BIND_SPARSE_INFO]; both names carry the same value.
     */
    VK_STRUCTURE_TYPE_DEVICE_GROUP_BIND_SPARSE_INFO_KHR(
        VK_STRUCTURE_TYPE_DEVICE_GROUP_BIND_SPARSE_INFO.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_BIND_BUFFER_MEMORY_DEVICE_GROUP_INFO]; both names carry the same
     * value.
     */
    VK_STRUCTURE_TYPE_BIND_BUFFER_MEMORY_DEVICE_GROUP_INFO_KHR(
        VK_STRUCTURE_TYPE_BIND_BUFFER_MEMORY_DEVICE_GROUP_INFO.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_BIND_IMAGE_MEMORY_DEVICE_GROUP_INFO]; both names carry the same
     * value.
     */
    VK_STRUCTURE_TYPE_BIND_IMAGE_MEMORY_DEVICE_GROUP_INFO_KHR(
        VK_STRUCTURE_TYPE_BIND_IMAGE_MEMORY_DEVICE_GROUP_INFO.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_TEXTURE_COMPRESSION_ASTC_HDR_FEATURES]; both
     * names carry the same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_TEXTURE_COMPRESSION_ASTC_HDR_FEATURES_EXT(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_TEXTURE_COMPRESSION_ASTC_HDR_FEATURES.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_GROUP_PROPERTIES]; both names carry the same
     * value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_GROUP_PROPERTIES_KHR(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_GROUP_PROPERTIES.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_DEVICE_GROUP_DEVICE_CREATE_INFO]; both names carry the same
     * value.
     */
    VK_STRUCTURE_TYPE_DEVICE_GROUP_DEVICE_CREATE_INFO_KHR(
        VK_STRUCTURE_TYPE_DEVICE_GROUP_DEVICE_CREATE_INFO.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_EXTERNAL_IMAGE_FORMAT_INFO]; both names carry the
     * same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_EXTERNAL_IMAGE_FORMAT_INFO_KHR(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_EXTERNAL_IMAGE_FORMAT_INFO.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_EXTERNAL_IMAGE_FORMAT_PROPERTIES]; both names carry the same
     * value.
     */
    VK_STRUCTURE_TYPE_EXTERNAL_IMAGE_FORMAT_PROPERTIES_KHR(
        VK_STRUCTURE_TYPE_EXTERNAL_IMAGE_FORMAT_PROPERTIES.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_EXTERNAL_BUFFER_INFO]; both names carry the same
     * value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_EXTERNAL_BUFFER_INFO_KHR(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_EXTERNAL_BUFFER_INFO.value,
    ),

    /** Alias of [VK_STRUCTURE_TYPE_EXTERNAL_BUFFER_PROPERTIES]; both names carry the same value. */
    VK_STRUCTURE_TYPE_EXTERNAL_BUFFER_PROPERTIES_KHR(VK_STRUCTURE_TYPE_EXTERNAL_BUFFER_PROPERTIES.value),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_ID_PROPERTIES]; both names carry the same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_ID_PROPERTIES_KHR(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_ID_PROPERTIES.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_BUFFER_CREATE_INFO]; both names carry the same
     * value.
     */
    VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_BUFFER_CREATE_INFO_KHR(
        VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_BUFFER_CREATE_INFO.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO]; both names carry the same
     * value.
     */
    VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO_KHR(
        VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_EXPORT_MEMORY_ALLOCATE_INFO]; both names carry the same value.
     */
    VK_STRUCTURE_TYPE_EXPORT_MEMORY_ALLOCATE_INFO_KHR(VK_STRUCTURE_TYPE_EXPORT_MEMORY_ALLOCATE_INFO.value),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_EXTERNAL_SEMAPHORE_INFO]; both names carry the
     * same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_EXTERNAL_SEMAPHORE_INFO_KHR(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_EXTERNAL_SEMAPHORE_INFO.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_EXTERNAL_SEMAPHORE_PROPERTIES]; both names carry the same value.
     */
    VK_STRUCTURE_TYPE_EXTERNAL_SEMAPHORE_PROPERTIES_KHR(
        VK_STRUCTURE_TYPE_EXTERNAL_SEMAPHORE_PROPERTIES.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_EXPORT_SEMAPHORE_CREATE_INFO]; both names carry the same value.
     */
    VK_STRUCTURE_TYPE_EXPORT_SEMAPHORE_CREATE_INFO_KHR(
        VK_STRUCTURE_TYPE_EXPORT_SEMAPHORE_CREATE_INFO.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_FLOAT16_INT8_FEATURES]; both names carry
     * the same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_FLOAT16_INT8_FEATURES_KHR(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_FLOAT16_INT8_FEATURES.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_FLOAT16_INT8_FEATURES]; both names carry
     * the same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FLOAT16_INT8_FEATURES_KHR(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_FLOAT16_INT8_FEATURES.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_16BIT_STORAGE_FEATURES]; both names carry the
     * same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_16BIT_STORAGE_FEATURES_KHR(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_16BIT_STORAGE_FEATURES.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_DESCRIPTOR_UPDATE_TEMPLATE_CREATE_INFO]; both names carry the
     * same value.
     */
    VK_STRUCTURE_TYPE_DESCRIPTOR_UPDATE_TEMPLATE_CREATE_INFO_KHR(
        VK_STRUCTURE_TYPE_DESCRIPTOR_UPDATE_TEMPLATE_CREATE_INFO.value,
    ),

    /** Alias of [VK_STRUCTURE_TYPE_SURFACE_CAPABILITIES_2_EXT]; both names carry the same value. */
    VK_STRUCTURE_TYPE_SURFACE_CAPABILITIES2_EXT(VK_STRUCTURE_TYPE_SURFACE_CAPABILITIES_2_EXT.value),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_IMAGELESS_FRAMEBUFFER_FEATURES]; both names carry
     * the same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_IMAGELESS_FRAMEBUFFER_FEATURES_KHR(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_IMAGELESS_FRAMEBUFFER_FEATURES.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_FRAMEBUFFER_ATTACHMENTS_CREATE_INFO]; both names carry the same
     * value.
     */
    VK_STRUCTURE_TYPE_FRAMEBUFFER_ATTACHMENTS_CREATE_INFO_KHR(
        VK_STRUCTURE_TYPE_FRAMEBUFFER_ATTACHMENTS_CREATE_INFO.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_FRAMEBUFFER_ATTACHMENT_IMAGE_INFO]; both names carry the same
     * value.
     */
    VK_STRUCTURE_TYPE_FRAMEBUFFER_ATTACHMENT_IMAGE_INFO_KHR(
        VK_STRUCTURE_TYPE_FRAMEBUFFER_ATTACHMENT_IMAGE_INFO.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_RENDER_PASS_ATTACHMENT_BEGIN_INFO]; both names carry the same
     * value.
     */
    VK_STRUCTURE_TYPE_RENDER_PASS_ATTACHMENT_BEGIN_INFO_KHR(
        VK_STRUCTURE_TYPE_RENDER_PASS_ATTACHMENT_BEGIN_INFO.value,
    ),

    /** Alias of [VK_STRUCTURE_TYPE_ATTACHMENT_DESCRIPTION_2]; both names carry the same value. */
    VK_STRUCTURE_TYPE_ATTACHMENT_DESCRIPTION_2_KHR(VK_STRUCTURE_TYPE_ATTACHMENT_DESCRIPTION_2.value),

    /** Alias of [VK_STRUCTURE_TYPE_ATTACHMENT_REFERENCE_2]; both names carry the same value. */
    VK_STRUCTURE_TYPE_ATTACHMENT_REFERENCE_2_KHR(VK_STRUCTURE_TYPE_ATTACHMENT_REFERENCE_2.value),

    /** Alias of [VK_STRUCTURE_TYPE_SUBPASS_DESCRIPTION_2]; both names carry the same value. */
    VK_STRUCTURE_TYPE_SUBPASS_DESCRIPTION_2_KHR(VK_STRUCTURE_TYPE_SUBPASS_DESCRIPTION_2.value),

    /** Alias of [VK_STRUCTURE_TYPE_SUBPASS_DEPENDENCY_2]; both names carry the same value. */
    VK_STRUCTURE_TYPE_SUBPASS_DEPENDENCY_2_KHR(VK_STRUCTURE_TYPE_SUBPASS_DEPENDENCY_2.value),

    /** Alias of [VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO_2]; both names carry the same value. */
    VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO_2_KHR(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO_2.value),

    /** Alias of [VK_STRUCTURE_TYPE_SUBPASS_BEGIN_INFO]; both names carry the same value. */
    VK_STRUCTURE_TYPE_SUBPASS_BEGIN_INFO_KHR(VK_STRUCTURE_TYPE_SUBPASS_BEGIN_INFO.value),

    /** Alias of [VK_STRUCTURE_TYPE_SUBPASS_END_INFO]; both names carry the same value. */
    VK_STRUCTURE_TYPE_SUBPASS_END_INFO_KHR(VK_STRUCTURE_TYPE_SUBPASS_END_INFO.value),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_EXTERNAL_FENCE_INFO]; both names carry the same
     * value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_EXTERNAL_FENCE_INFO_KHR(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_EXTERNAL_FENCE_INFO.value,
    ),

    /** Alias of [VK_STRUCTURE_TYPE_EXTERNAL_FENCE_PROPERTIES]; both names carry the same value. */
    VK_STRUCTURE_TYPE_EXTERNAL_FENCE_PROPERTIES_KHR(VK_STRUCTURE_TYPE_EXTERNAL_FENCE_PROPERTIES.value),

    /** Alias of [VK_STRUCTURE_TYPE_EXPORT_FENCE_CREATE_INFO]; both names carry the same value. */
    VK_STRUCTURE_TYPE_EXPORT_FENCE_CREATE_INFO_KHR(VK_STRUCTURE_TYPE_EXPORT_FENCE_CREATE_INFO.value),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_POINT_CLIPPING_PROPERTIES]; both names carry the
     * same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_POINT_CLIPPING_PROPERTIES_KHR(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_POINT_CLIPPING_PROPERTIES.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_RENDER_PASS_INPUT_ATTACHMENT_ASPECT_CREATE_INFO]; both names
     * carry the same value.
     */
    VK_STRUCTURE_TYPE_RENDER_PASS_INPUT_ATTACHMENT_ASPECT_CREATE_INFO_KHR(
        VK_STRUCTURE_TYPE_RENDER_PASS_INPUT_ATTACHMENT_ASPECT_CREATE_INFO.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_IMAGE_VIEW_USAGE_CREATE_INFO]; both names carry the same value.
     */
    VK_STRUCTURE_TYPE_IMAGE_VIEW_USAGE_CREATE_INFO_KHR(
        VK_STRUCTURE_TYPE_IMAGE_VIEW_USAGE_CREATE_INFO.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PIPELINE_TESSELLATION_DOMAIN_ORIGIN_STATE_CREATE_INFO]; both
     * names carry the same value.
     */
    VK_STRUCTURE_TYPE_PIPELINE_TESSELLATION_DOMAIN_ORIGIN_STATE_CREATE_INFO_KHR(
        VK_STRUCTURE_TYPE_PIPELINE_TESSELLATION_DOMAIN_ORIGIN_STATE_CREATE_INFO.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VARIABLE_POINTERS_FEATURES]; both names carry the
     * same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VARIABLE_POINTERS_FEATURES_KHR(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VARIABLE_POINTERS_FEATURES.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VARIABLE_POINTERS_FEATURES_KHR]; both names carry
     * the same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VARIABLE_POINTER_FEATURES_KHR(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VARIABLE_POINTERS_FEATURES_KHR.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_MEMORY_DEDICATED_REQUIREMENTS]; both names carry the same value.
     */
    VK_STRUCTURE_TYPE_MEMORY_DEDICATED_REQUIREMENTS_KHR(
        VK_STRUCTURE_TYPE_MEMORY_DEDICATED_REQUIREMENTS.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_MEMORY_DEDICATED_ALLOCATE_INFO]; both names carry the same value.
     */
    VK_STRUCTURE_TYPE_MEMORY_DEDICATED_ALLOCATE_INFO_KHR(
        VK_STRUCTURE_TYPE_MEMORY_DEDICATED_ALLOCATE_INFO.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SAMPLER_FILTER_MINMAX_PROPERTIES]; both names
     * carry the same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SAMPLER_FILTER_MINMAX_PROPERTIES_EXT(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SAMPLER_FILTER_MINMAX_PROPERTIES.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_SAMPLER_REDUCTION_MODE_CREATE_INFO]; both names carry the same
     * value.
     */
    VK_STRUCTURE_TYPE_SAMPLER_REDUCTION_MODE_CREATE_INFO_EXT(
        VK_STRUCTURE_TYPE_SAMPLER_REDUCTION_MODE_CREATE_INFO.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_INLINE_UNIFORM_BLOCK_FEATURES]; both names carry
     * the same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_INLINE_UNIFORM_BLOCK_FEATURES_EXT(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_INLINE_UNIFORM_BLOCK_FEATURES.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_INLINE_UNIFORM_BLOCK_PROPERTIES]; both names
     * carry the same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_INLINE_UNIFORM_BLOCK_PROPERTIES_EXT(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_INLINE_UNIFORM_BLOCK_PROPERTIES.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET_INLINE_UNIFORM_BLOCK]; both names carry the
     * same value.
     */
    VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET_INLINE_UNIFORM_BLOCK_EXT(
        VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET_INLINE_UNIFORM_BLOCK.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_INLINE_UNIFORM_BLOCK_CREATE_INFO]; both names
     * carry the same value.
     */
    VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_INLINE_UNIFORM_BLOCK_CREATE_INFO_EXT(
        VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_INLINE_UNIFORM_BLOCK_CREATE_INFO.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_BUFFER_MEMORY_REQUIREMENTS_INFO_2]; both names carry the same
     * value.
     */
    VK_STRUCTURE_TYPE_BUFFER_MEMORY_REQUIREMENTS_INFO_2_KHR(
        VK_STRUCTURE_TYPE_BUFFER_MEMORY_REQUIREMENTS_INFO_2.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_IMAGE_MEMORY_REQUIREMENTS_INFO_2]; both names carry the same
     * value.
     */
    VK_STRUCTURE_TYPE_IMAGE_MEMORY_REQUIREMENTS_INFO_2_KHR(
        VK_STRUCTURE_TYPE_IMAGE_MEMORY_REQUIREMENTS_INFO_2.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_IMAGE_SPARSE_MEMORY_REQUIREMENTS_INFO_2]; both names carry the
     * same value.
     */
    VK_STRUCTURE_TYPE_IMAGE_SPARSE_MEMORY_REQUIREMENTS_INFO_2_KHR(
        VK_STRUCTURE_TYPE_IMAGE_SPARSE_MEMORY_REQUIREMENTS_INFO_2.value,
    ),

    /** Alias of [VK_STRUCTURE_TYPE_MEMORY_REQUIREMENTS_2]; both names carry the same value. */
    VK_STRUCTURE_TYPE_MEMORY_REQUIREMENTS_2_KHR(VK_STRUCTURE_TYPE_MEMORY_REQUIREMENTS_2.value),

    /**
     * Alias of [VK_STRUCTURE_TYPE_SPARSE_IMAGE_MEMORY_REQUIREMENTS_2]; both names carry the same
     * value.
     */
    VK_STRUCTURE_TYPE_SPARSE_IMAGE_MEMORY_REQUIREMENTS_2_KHR(
        VK_STRUCTURE_TYPE_SPARSE_IMAGE_MEMORY_REQUIREMENTS_2.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_IMAGE_FORMAT_LIST_CREATE_INFO]; both names carry the same value.
     */
    VK_STRUCTURE_TYPE_IMAGE_FORMAT_LIST_CREATE_INFO_KHR(
        VK_STRUCTURE_TYPE_IMAGE_FORMAT_LIST_CREATE_INFO.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_SAMPLER_YCBCR_CONVERSION_CREATE_INFO]; both names carry the same
     * value.
     */
    VK_STRUCTURE_TYPE_SAMPLER_YCBCR_CONVERSION_CREATE_INFO_KHR(
        VK_STRUCTURE_TYPE_SAMPLER_YCBCR_CONVERSION_CREATE_INFO.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_SAMPLER_YCBCR_CONVERSION_INFO]; both names carry the same value.
     */
    VK_STRUCTURE_TYPE_SAMPLER_YCBCR_CONVERSION_INFO_KHR(
        VK_STRUCTURE_TYPE_SAMPLER_YCBCR_CONVERSION_INFO.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_BIND_IMAGE_PLANE_MEMORY_INFO]; both names carry the same value.
     */
    VK_STRUCTURE_TYPE_BIND_IMAGE_PLANE_MEMORY_INFO_KHR(
        VK_STRUCTURE_TYPE_BIND_IMAGE_PLANE_MEMORY_INFO.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_IMAGE_PLANE_MEMORY_REQUIREMENTS_INFO]; both names carry the same
     * value.
     */
    VK_STRUCTURE_TYPE_IMAGE_PLANE_MEMORY_REQUIREMENTS_INFO_KHR(
        VK_STRUCTURE_TYPE_IMAGE_PLANE_MEMORY_REQUIREMENTS_INFO.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SAMPLER_YCBCR_CONVERSION_FEATURES]; both names
     * carry the same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SAMPLER_YCBCR_CONVERSION_FEATURES_KHR(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SAMPLER_YCBCR_CONVERSION_FEATURES.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_SAMPLER_YCBCR_CONVERSION_IMAGE_FORMAT_PROPERTIES]; both names
     * carry the same value.
     */
    VK_STRUCTURE_TYPE_SAMPLER_YCBCR_CONVERSION_IMAGE_FORMAT_PROPERTIES_KHR(
        VK_STRUCTURE_TYPE_SAMPLER_YCBCR_CONVERSION_IMAGE_FORMAT_PROPERTIES.value,
    ),

    /** Alias of [VK_STRUCTURE_TYPE_BIND_BUFFER_MEMORY_INFO]; both names carry the same value. */
    VK_STRUCTURE_TYPE_BIND_BUFFER_MEMORY_INFO_KHR(VK_STRUCTURE_TYPE_BIND_BUFFER_MEMORY_INFO.value),

    /** Alias of [VK_STRUCTURE_TYPE_BIND_IMAGE_MEMORY_INFO]; both names carry the same value. */
    VK_STRUCTURE_TYPE_BIND_IMAGE_MEMORY_INFO_KHR(VK_STRUCTURE_TYPE_BIND_IMAGE_MEMORY_INFO.value),

    /**
     * Alias of [VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_BINDING_FLAGS_CREATE_INFO]; both names
     * carry the same value.
     */
    VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_BINDING_FLAGS_CREATE_INFO_EXT(
        VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_BINDING_FLAGS_CREATE_INFO.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DESCRIPTOR_INDEXING_FEATURES]; both names carry
     * the same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DESCRIPTOR_INDEXING_FEATURES_EXT(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DESCRIPTOR_INDEXING_FEATURES.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DESCRIPTOR_INDEXING_PROPERTIES]; both names carry
     * the same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DESCRIPTOR_INDEXING_PROPERTIES_EXT(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DESCRIPTOR_INDEXING_PROPERTIES.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_DESCRIPTOR_SET_VARIABLE_DESCRIPTOR_COUNT_ALLOCATE_INFO]; both
     * names carry the same value.
     */
    VK_STRUCTURE_TYPE_DESCRIPTOR_SET_VARIABLE_DESCRIPTOR_COUNT_ALLOCATE_INFO_EXT(
        VK_STRUCTURE_TYPE_DESCRIPTOR_SET_VARIABLE_DESCRIPTOR_COUNT_ALLOCATE_INFO.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_DESCRIPTOR_SET_VARIABLE_DESCRIPTOR_COUNT_LAYOUT_SUPPORT]; both
     * names carry the same value.
     */
    VK_STRUCTURE_TYPE_DESCRIPTOR_SET_VARIABLE_DESCRIPTOR_COUNT_LAYOUT_SUPPORT_EXT(
        VK_STRUCTURE_TYPE_DESCRIPTOR_SET_VARIABLE_DESCRIPTOR_COUNT_LAYOUT_SUPPORT.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MAINTENANCE_3_PROPERTIES]; both names carry the
     * same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MAINTENANCE_3_PROPERTIES_KHR(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MAINTENANCE_3_PROPERTIES.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_SUPPORT]; both names carry the same value.
     */
    VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_SUPPORT_KHR(
        VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_SUPPORT.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_DEVICE_QUEUE_GLOBAL_PRIORITY_CREATE_INFO_KHR]; both names carry
     * the same value.
     */
    VK_STRUCTURE_TYPE_DEVICE_QUEUE_GLOBAL_PRIORITY_CREATE_INFO_EXT(
        VK_STRUCTURE_TYPE_DEVICE_QUEUE_GLOBAL_PRIORITY_CREATE_INFO_KHR.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_SUBGROUP_EXTENDED_TYPES_FEATURES]; both
     * names carry the same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_SUBGROUP_EXTENDED_TYPES_FEATURES_KHR(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_SUBGROUP_EXTENDED_TYPES_FEATURES.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_8BIT_STORAGE_FEATURES]; both names carry the same
     * value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_8BIT_STORAGE_FEATURES_KHR(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_8BIT_STORAGE_FEATURES.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_ATOMIC_INT64_FEATURES]; both names carry
     * the same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_ATOMIC_INT64_FEATURES_KHR(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_ATOMIC_INT64_FEATURES.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PIPELINE_CREATION_FEEDBACK_CREATE_INFO]; both names carry the
     * same value.
     */
    VK_STRUCTURE_TYPE_PIPELINE_CREATION_FEEDBACK_CREATE_INFO_EXT(
        VK_STRUCTURE_TYPE_PIPELINE_CREATION_FEEDBACK_CREATE_INFO.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DRIVER_PROPERTIES]; both names carry the same
     * value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DRIVER_PROPERTIES_KHR(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DRIVER_PROPERTIES.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FLOAT_CONTROLS_PROPERTIES]; both names carry the
     * same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FLOAT_CONTROLS_PROPERTIES_KHR(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FLOAT_CONTROLS_PROPERTIES.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DEPTH_STENCIL_RESOLVE_PROPERTIES]; both names
     * carry the same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DEPTH_STENCIL_RESOLVE_PROPERTIES_KHR(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DEPTH_STENCIL_RESOLVE_PROPERTIES.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_SUBPASS_DESCRIPTION_DEPTH_STENCIL_RESOLVE]; both names carry the
     * same value.
     */
    VK_STRUCTURE_TYPE_SUBPASS_DESCRIPTION_DEPTH_STENCIL_RESOLVE_KHR(
        VK_STRUCTURE_TYPE_SUBPASS_DESCRIPTION_DEPTH_STENCIL_RESOLVE.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_TIMELINE_SEMAPHORE_FEATURES]; both names carry
     * the same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_TIMELINE_SEMAPHORE_FEATURES_KHR(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_TIMELINE_SEMAPHORE_FEATURES.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_TIMELINE_SEMAPHORE_PROPERTIES]; both names carry
     * the same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_TIMELINE_SEMAPHORE_PROPERTIES_KHR(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_TIMELINE_SEMAPHORE_PROPERTIES.value,
    ),

    /** Alias of [VK_STRUCTURE_TYPE_SEMAPHORE_TYPE_CREATE_INFO]; both names carry the same value. */
    VK_STRUCTURE_TYPE_SEMAPHORE_TYPE_CREATE_INFO_KHR(VK_STRUCTURE_TYPE_SEMAPHORE_TYPE_CREATE_INFO.value),

    /**
     * Alias of [VK_STRUCTURE_TYPE_TIMELINE_SEMAPHORE_SUBMIT_INFO]; both names carry the same value.
     */
    VK_STRUCTURE_TYPE_TIMELINE_SEMAPHORE_SUBMIT_INFO_KHR(
        VK_STRUCTURE_TYPE_TIMELINE_SEMAPHORE_SUBMIT_INFO.value,
    ),

    /** Alias of [VK_STRUCTURE_TYPE_SEMAPHORE_WAIT_INFO]; both names carry the same value. */
    VK_STRUCTURE_TYPE_SEMAPHORE_WAIT_INFO_KHR(VK_STRUCTURE_TYPE_SEMAPHORE_WAIT_INFO.value),

    /** Alias of [VK_STRUCTURE_TYPE_SEMAPHORE_SIGNAL_INFO]; both names carry the same value. */
    VK_STRUCTURE_TYPE_SEMAPHORE_SIGNAL_INFO_KHR(VK_STRUCTURE_TYPE_SEMAPHORE_SIGNAL_INFO.value),

    /**
     * Alias of [VK_STRUCTURE_TYPE_QUERY_POOL_PERFORMANCE_QUERY_CREATE_INFO_INTEL]; both names carry
     * the same value.
     */
    VK_STRUCTURE_TYPE_QUERY_POOL_CREATE_INFO_INTEL(
        VK_STRUCTURE_TYPE_QUERY_POOL_PERFORMANCE_QUERY_CREATE_INFO_INTEL.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_MEMORY_MODEL_FEATURES]; both names carry
     * the same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_MEMORY_MODEL_FEATURES_KHR(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_MEMORY_MODEL_FEATURES.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_TERMINATE_INVOCATION_FEATURES]; both names
     * carry the same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_TERMINATE_INVOCATION_FEATURES_KHR(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_TERMINATE_INVOCATION_FEATURES.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SCALAR_BLOCK_LAYOUT_FEATURES]; both names carry
     * the same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SCALAR_BLOCK_LAYOUT_FEATURES_EXT(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SCALAR_BLOCK_LAYOUT_FEATURES.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SUBGROUP_SIZE_CONTROL_PROPERTIES]; both names
     * carry the same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SUBGROUP_SIZE_CONTROL_PROPERTIES_EXT(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SUBGROUP_SIZE_CONTROL_PROPERTIES.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_REQUIRED_SUBGROUP_SIZE_CREATE_INFO]; both
     * names carry the same value.
     */
    VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_REQUIRED_SUBGROUP_SIZE_CREATE_INFO_EXT(
        VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_REQUIRED_SUBGROUP_SIZE_CREATE_INFO.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SUBGROUP_SIZE_CONTROL_FEATURES]; both names carry
     * the same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SUBGROUP_SIZE_CONTROL_FEATURES_EXT(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SUBGROUP_SIZE_CONTROL_FEATURES.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SEPARATE_DEPTH_STENCIL_LAYOUTS_FEATURES]; both
     * names carry the same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SEPARATE_DEPTH_STENCIL_LAYOUTS_FEATURES_KHR(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SEPARATE_DEPTH_STENCIL_LAYOUTS_FEATURES.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_ATTACHMENT_REFERENCE_STENCIL_LAYOUT]; both names carry the same
     * value.
     */
    VK_STRUCTURE_TYPE_ATTACHMENT_REFERENCE_STENCIL_LAYOUT_KHR(
        VK_STRUCTURE_TYPE_ATTACHMENT_REFERENCE_STENCIL_LAYOUT.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_ATTACHMENT_DESCRIPTION_STENCIL_LAYOUT]; both names carry the same
     * value.
     */
    VK_STRUCTURE_TYPE_ATTACHMENT_DESCRIPTION_STENCIL_LAYOUT_KHR(
        VK_STRUCTURE_TYPE_ATTACHMENT_DESCRIPTION_STENCIL_LAYOUT.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_BUFFER_DEVICE_ADDRESS_FEATURES_EXT]; both names
     * carry the same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_BUFFER_ADDRESS_FEATURES_EXT(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_BUFFER_DEVICE_ADDRESS_FEATURES_EXT.value,
    ),

    /** Alias of [VK_STRUCTURE_TYPE_BUFFER_DEVICE_ADDRESS_INFO]; both names carry the same value. */
    VK_STRUCTURE_TYPE_BUFFER_DEVICE_ADDRESS_INFO_EXT(VK_STRUCTURE_TYPE_BUFFER_DEVICE_ADDRESS_INFO.value),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_TOOL_PROPERTIES]; both names carry the same
     * value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_TOOL_PROPERTIES_EXT(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_TOOL_PROPERTIES.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_IMAGE_STENCIL_USAGE_CREATE_INFO]; both names carry the same
     * value.
     */
    VK_STRUCTURE_TYPE_IMAGE_STENCIL_USAGE_CREATE_INFO_EXT(
        VK_STRUCTURE_TYPE_IMAGE_STENCIL_USAGE_CREATE_INFO.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_UNIFORM_BUFFER_STANDARD_LAYOUT_FEATURES]; both
     * names carry the same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_UNIFORM_BUFFER_STANDARD_LAYOUT_FEATURES_KHR(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_UNIFORM_BUFFER_STANDARD_LAYOUT_FEATURES.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_BUFFER_DEVICE_ADDRESS_FEATURES]; both names carry
     * the same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_BUFFER_DEVICE_ADDRESS_FEATURES_KHR(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_BUFFER_DEVICE_ADDRESS_FEATURES.value,
    ),

    /** Alias of [VK_STRUCTURE_TYPE_BUFFER_DEVICE_ADDRESS_INFO]; both names carry the same value. */
    VK_STRUCTURE_TYPE_BUFFER_DEVICE_ADDRESS_INFO_KHR(VK_STRUCTURE_TYPE_BUFFER_DEVICE_ADDRESS_INFO.value),

    /**
     * Alias of [VK_STRUCTURE_TYPE_BUFFER_OPAQUE_CAPTURE_ADDRESS_CREATE_INFO]; both names carry the
     * same value.
     */
    VK_STRUCTURE_TYPE_BUFFER_OPAQUE_CAPTURE_ADDRESS_CREATE_INFO_KHR(
        VK_STRUCTURE_TYPE_BUFFER_OPAQUE_CAPTURE_ADDRESS_CREATE_INFO.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_MEMORY_OPAQUE_CAPTURE_ADDRESS_ALLOCATE_INFO]; both names carry
     * the same value.
     */
    VK_STRUCTURE_TYPE_MEMORY_OPAQUE_CAPTURE_ADDRESS_ALLOCATE_INFO_KHR(
        VK_STRUCTURE_TYPE_MEMORY_OPAQUE_CAPTURE_ADDRESS_ALLOCATE_INFO.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_DEVICE_MEMORY_OPAQUE_CAPTURE_ADDRESS_INFO]; both names carry the
     * same value.
     */
    VK_STRUCTURE_TYPE_DEVICE_MEMORY_OPAQUE_CAPTURE_ADDRESS_INFO_KHR(
        VK_STRUCTURE_TYPE_DEVICE_MEMORY_OPAQUE_CAPTURE_ADDRESS_INFO.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_HOST_QUERY_RESET_FEATURES]; both names carry the
     * same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_HOST_QUERY_RESET_FEATURES_EXT(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_HOST_QUERY_RESET_FEATURES.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_DEMOTE_TO_HELPER_INVOCATION_FEATURES];
     * both names carry the same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_DEMOTE_TO_HELPER_INVOCATION_FEATURES_EXT(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_DEMOTE_TO_HELPER_INVOCATION_FEATURES.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_INTEGER_DOT_PRODUCT_FEATURES]; both names
     * carry the same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_INTEGER_DOT_PRODUCT_FEATURES_KHR(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_INTEGER_DOT_PRODUCT_FEATURES.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_INTEGER_DOT_PRODUCT_PROPERTIES]; both
     * names carry the same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_INTEGER_DOT_PRODUCT_PROPERTIES_KHR(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_INTEGER_DOT_PRODUCT_PROPERTIES.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_TEXEL_BUFFER_ALIGNMENT_PROPERTIES]; both names
     * carry the same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_TEXEL_BUFFER_ALIGNMENT_PROPERTIES_EXT(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_TEXEL_BUFFER_ALIGNMENT_PROPERTIES.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PRIVATE_DATA_FEATURES]; both names carry the same
     * value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PRIVATE_DATA_FEATURES_EXT(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PRIVATE_DATA_FEATURES.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_DEVICE_PRIVATE_DATA_CREATE_INFO]; both names carry the same
     * value.
     */
    VK_STRUCTURE_TYPE_DEVICE_PRIVATE_DATA_CREATE_INFO_EXT(
        VK_STRUCTURE_TYPE_DEVICE_PRIVATE_DATA_CREATE_INFO.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PRIVATE_DATA_SLOT_CREATE_INFO]; both names carry the same value.
     */
    VK_STRUCTURE_TYPE_PRIVATE_DATA_SLOT_CREATE_INFO_EXT(
        VK_STRUCTURE_TYPE_PRIVATE_DATA_SLOT_CREATE_INFO.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PIPELINE_CREATION_CACHE_CONTROL_FEATURES]; both
     * names carry the same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PIPELINE_CREATION_CACHE_CONTROL_FEATURES_EXT(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PIPELINE_CREATION_CACHE_CONTROL_FEATURES.value,
    ),

    /** Alias of [VK_STRUCTURE_TYPE_MEMORY_BARRIER_2]; both names carry the same value. */
    VK_STRUCTURE_TYPE_MEMORY_BARRIER_2_KHR(VK_STRUCTURE_TYPE_MEMORY_BARRIER_2.value),

    /** Alias of [VK_STRUCTURE_TYPE_BUFFER_MEMORY_BARRIER_2]; both names carry the same value. */
    VK_STRUCTURE_TYPE_BUFFER_MEMORY_BARRIER_2_KHR(VK_STRUCTURE_TYPE_BUFFER_MEMORY_BARRIER_2.value),

    /** Alias of [VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER_2]; both names carry the same value. */
    VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER_2_KHR(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER_2.value),

    /** Alias of [VK_STRUCTURE_TYPE_DEPENDENCY_INFO]; both names carry the same value. */
    VK_STRUCTURE_TYPE_DEPENDENCY_INFO_KHR(VK_STRUCTURE_TYPE_DEPENDENCY_INFO.value),

    /** Alias of [VK_STRUCTURE_TYPE_SUBMIT_INFO_2]; both names carry the same value. */
    VK_STRUCTURE_TYPE_SUBMIT_INFO_2_KHR(VK_STRUCTURE_TYPE_SUBMIT_INFO_2.value),

    /** Alias of [VK_STRUCTURE_TYPE_SEMAPHORE_SUBMIT_INFO]; both names carry the same value. */
    VK_STRUCTURE_TYPE_SEMAPHORE_SUBMIT_INFO_KHR(VK_STRUCTURE_TYPE_SEMAPHORE_SUBMIT_INFO.value),

    /** Alias of [VK_STRUCTURE_TYPE_COMMAND_BUFFER_SUBMIT_INFO]; both names carry the same value. */
    VK_STRUCTURE_TYPE_COMMAND_BUFFER_SUBMIT_INFO_KHR(VK_STRUCTURE_TYPE_COMMAND_BUFFER_SUBMIT_INFO.value),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SYNCHRONIZATION_2_FEATURES]; both names carry the
     * same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SYNCHRONIZATION_2_FEATURES_KHR(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SYNCHRONIZATION_2_FEATURES.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_ZERO_INITIALIZE_WORKGROUP_MEMORY_FEATURES]; both
     * names carry the same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_ZERO_INITIALIZE_WORKGROUP_MEMORY_FEATURES_KHR(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_ZERO_INITIALIZE_WORKGROUP_MEMORY_FEATURES.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_IMAGE_ROBUSTNESS_FEATURES]; both names carry the
     * same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_IMAGE_ROBUSTNESS_FEATURES_EXT(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_IMAGE_ROBUSTNESS_FEATURES.value,
    ),

    /** Alias of [VK_STRUCTURE_TYPE_COPY_BUFFER_INFO_2]; both names carry the same value. */
    VK_STRUCTURE_TYPE_COPY_BUFFER_INFO_2_KHR(VK_STRUCTURE_TYPE_COPY_BUFFER_INFO_2.value),

    /** Alias of [VK_STRUCTURE_TYPE_COPY_IMAGE_INFO_2]; both names carry the same value. */
    VK_STRUCTURE_TYPE_COPY_IMAGE_INFO_2_KHR(VK_STRUCTURE_TYPE_COPY_IMAGE_INFO_2.value),

    /**
     * Alias of [VK_STRUCTURE_TYPE_COPY_BUFFER_TO_IMAGE_INFO_2]; both names carry the same value.
     */
    VK_STRUCTURE_TYPE_COPY_BUFFER_TO_IMAGE_INFO_2_KHR(VK_STRUCTURE_TYPE_COPY_BUFFER_TO_IMAGE_INFO_2.value),

    /**
     * Alias of [VK_STRUCTURE_TYPE_COPY_IMAGE_TO_BUFFER_INFO_2]; both names carry the same value.
     */
    VK_STRUCTURE_TYPE_COPY_IMAGE_TO_BUFFER_INFO_2_KHR(VK_STRUCTURE_TYPE_COPY_IMAGE_TO_BUFFER_INFO_2.value),

    /** Alias of [VK_STRUCTURE_TYPE_BLIT_IMAGE_INFO_2]; both names carry the same value. */
    VK_STRUCTURE_TYPE_BLIT_IMAGE_INFO_2_KHR(VK_STRUCTURE_TYPE_BLIT_IMAGE_INFO_2.value),

    /** Alias of [VK_STRUCTURE_TYPE_RESOLVE_IMAGE_INFO_2]; both names carry the same value. */
    VK_STRUCTURE_TYPE_RESOLVE_IMAGE_INFO_2_KHR(VK_STRUCTURE_TYPE_RESOLVE_IMAGE_INFO_2.value),

    /** Alias of [VK_STRUCTURE_TYPE_BUFFER_COPY_2]; both names carry the same value. */
    VK_STRUCTURE_TYPE_BUFFER_COPY_2_KHR(VK_STRUCTURE_TYPE_BUFFER_COPY_2.value),

    /** Alias of [VK_STRUCTURE_TYPE_IMAGE_COPY_2]; both names carry the same value. */
    VK_STRUCTURE_TYPE_IMAGE_COPY_2_KHR(VK_STRUCTURE_TYPE_IMAGE_COPY_2.value),

    /** Alias of [VK_STRUCTURE_TYPE_IMAGE_BLIT_2]; both names carry the same value. */
    VK_STRUCTURE_TYPE_IMAGE_BLIT_2_KHR(VK_STRUCTURE_TYPE_IMAGE_BLIT_2.value),

    /** Alias of [VK_STRUCTURE_TYPE_BUFFER_IMAGE_COPY_2]; both names carry the same value. */
    VK_STRUCTURE_TYPE_BUFFER_IMAGE_COPY_2_KHR(VK_STRUCTURE_TYPE_BUFFER_IMAGE_COPY_2.value),

    /** Alias of [VK_STRUCTURE_TYPE_IMAGE_RESOLVE_2]; both names carry the same value. */
    VK_STRUCTURE_TYPE_IMAGE_RESOLVE_2_KHR(VK_STRUCTURE_TYPE_IMAGE_RESOLVE_2.value),

    /** Alias of [VK_STRUCTURE_TYPE_FORMAT_PROPERTIES_3]; both names carry the same value. */
    VK_STRUCTURE_TYPE_FORMAT_PROPERTIES_3_KHR(VK_STRUCTURE_TYPE_FORMAT_PROPERTIES_3.value),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_GLOBAL_PRIORITY_QUERY_FEATURES_KHR]; both names
     * carry the same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_GLOBAL_PRIORITY_QUERY_FEATURES_EXT(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_GLOBAL_PRIORITY_QUERY_FEATURES_KHR.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_QUEUE_FAMILY_GLOBAL_PRIORITY_PROPERTIES_KHR]; both names carry
     * the same value.
     */
    VK_STRUCTURE_TYPE_QUEUE_FAMILY_GLOBAL_PRIORITY_PROPERTIES_EXT(
        VK_STRUCTURE_TYPE_QUEUE_FAMILY_GLOBAL_PRIORITY_PROPERTIES_KHR.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MAINTENANCE_4_FEATURES]; both names carry the
     * same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MAINTENANCE_4_FEATURES_KHR(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MAINTENANCE_4_FEATURES.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MAINTENANCE_4_PROPERTIES]; both names carry the
     * same value.
     */
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MAINTENANCE_4_PROPERTIES_KHR(
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MAINTENANCE_4_PROPERTIES.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_DEVICE_BUFFER_MEMORY_REQUIREMENTS]; both names carry the same
     * value.
     */
    VK_STRUCTURE_TYPE_DEVICE_BUFFER_MEMORY_REQUIREMENTS_KHR(
        VK_STRUCTURE_TYPE_DEVICE_BUFFER_MEMORY_REQUIREMENTS.value,
    ),

    /**
     * Alias of [VK_STRUCTURE_TYPE_DEVICE_IMAGE_MEMORY_REQUIREMENTS]; both names carry the same
     * value.
     */
    VK_STRUCTURE_TYPE_DEVICE_IMAGE_MEMORY_REQUIREMENTS_KHR(
        VK_STRUCTURE_TYPE_DEVICE_IMAGE_MEMORY_REQUIREMENTS.value,
    ),

    /** Sentinel that forces the C enum to 32 bits; it is never a valid value. */
    VK_STRUCTURE_TYPE_MAX_ENUM(0x7FFFFFFF),
}

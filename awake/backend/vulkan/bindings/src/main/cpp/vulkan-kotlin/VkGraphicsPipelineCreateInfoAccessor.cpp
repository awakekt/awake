/*
 *  VkGraphicsPipelineCreateInfoAccessor.cpp
 *  Vulkan accessor for VkGraphicsPipelineCreateInfo
 *  Created by Ron June Valdoz */

#include <includes/VkGraphicsPipelineCreateInfoAccessor.h>
#include <includes/VulkanAccessArena.h>

VkGraphicsPipelineCreateInfoAccessor::VkGraphicsPipelineCreateInfoAccessor(JNIEnv *env, jobject obj)
        : env(env), obj(obj) {
    clazz = env->GetObjectClass(obj);
    sTypeField = env->GetFieldID(clazz, "sType",
                                 "Lcom/awakekt/awake/vulkan/enums/VkStructureType;");
    pNextField = env->GetFieldID(clazz, "pNext", "Ljava/lang/Object;");
    flagsField = env->GetFieldID(clazz, "flags", "I");
    pStagesField = env->GetFieldID(clazz, "pStages",
                                   "[Lcom/awakekt/awake/vulkan/models/info/pipeline/VkPipelineShaderStageCreateInfo;");
    pVertexInputStateField = env->GetFieldID(clazz, "pVertexInputState",
                                             "[Lcom/awakekt/awake/vulkan/models/info/pipeline/VkPipelineVertexInputStateCreateInfo;");
    pInputAssemblyStateField = env->GetFieldID(clazz, "pInputAssemblyState",
                                               "[Lcom/awakekt/awake/vulkan/models/info/pipeline/VkPipelineInputAssemblyStateCreateInfo;");
    pTessellationStateField = env->GetFieldID(clazz, "pTessellationState",
                                              "[Lcom/awakekt/awake/vulkan/models/info/pipeline/VkPipelineTessellationStateCreateInfo;");
    pViewportStateField = env->GetFieldID(clazz, "pViewportState",
                                          "[Lcom/awakekt/awake/vulkan/models/info/pipeline/VkPipelineViewportStateCreateInfo;");
    pRasterizationStateField = env->GetFieldID(clazz, "pRasterizationState",
                                               "[Lcom/awakekt/awake/vulkan/models/info/pipeline/VkPipelineRasterizationStateCreateInfo;");
    pMultisampleStateField = env->GetFieldID(clazz, "pMultisampleState",
                                             "[Lcom/awakekt/awake/vulkan/models/info/pipeline/VkPipelineMultisampleStateCreateInfo;");
    pDepthStencilStateField = env->GetFieldID(clazz, "pDepthStencilState",
                                              "[Lcom/awakekt/awake/vulkan/models/info/pipeline/VkPipelineDepthStencilStateCreateInfo;");
    pColorBlendStateField = env->GetFieldID(clazz, "pColorBlendState",
                                            "[Lcom/awakekt/awake/vulkan/models/info/pipeline/VkPipelineColorBlendStateCreateInfo;");
    pDynamicStateField = env->GetFieldID(clazz, "pDynamicState",
                                         "[Lcom/awakekt/awake/vulkan/models/info/pipeline/VkPipelineDynamicStateCreateInfo;");
    layoutField = env->GetFieldID(clazz, "layout", "J");
    renderPassField = env->GetFieldID(clazz, "renderPass", "J");
    subpassField = env->GetFieldID(clazz, "subpass", "I");
    basePipelineHandleField = env->GetFieldID(clazz, "basePipelineHandle", "J");
    basePipelineIndexField = env->GetFieldID(clazz, "basePipelineIndex", "I");
}

void
VkGraphicsPipelineCreateInfoAccessor::getpStages(VkGraphicsPipelineCreateInfo &clazzInfo) {
    auto pStagesArray = (jobjectArray) env->GetObjectField(obj, pStagesField);
    if (pStagesArray == nullptr) {
        clazzInfo.stageCount = 0;
        clazzInfo.pStages = nullptr;
        return;
    }
    auto size = env->GetArrayLength(pStagesArray);
    if (size <= 0) {
        clazzInfo.stageCount = 0;
        clazzInfo.pStages = nullptr;
        env->DeleteLocalRef(pStagesArray);
        return;
    }
    auto count = static_cast<std::size_t>(size);
    auto *copy = awake::VulkanAccessArena::current().allocArray<VkPipelineShaderStageCreateInfo>(count);
    for (int i = 0; i < size; ++i) {
        auto element = (jobject) env->GetObjectArrayElement(pStagesArray, i);
        VkPipelineShaderStageCreateInfoAccessor accessor(env, element);
        accessor.fromObject(copy[i]);
        env->DeleteLocalRef(element);
    }
    clazzInfo.stageCount = static_cast<uint32_t>(count);
    clazzInfo.pStages = copy;
    env->DeleteLocalRef(pStagesArray);
}

VkPipeline
VkGraphicsPipelineCreateInfoAccessor::getbasePipelineHandle() {
    auto value = env->GetLongField(obj, basePipelineHandleField);
    if (value == 0) {
        return VK_NULL_HANDLE;
    }
    return reinterpret_cast<VkPipeline>(value); // VkHandle
}

void
VkGraphicsPipelineCreateInfoAccessor::getpTessellationState(
        VkGraphicsPipelineCreateInfo &clazzInfo) {
    auto pTessellationStateArray = (jobjectArray) env->GetObjectField(obj, pTessellationStateField);
    if (pTessellationStateArray == nullptr) {
        clazzInfo.pTessellationState = nullptr;
        return;
    }
    auto size = env->GetArrayLength(pTessellationStateArray);
    if (size <= 0) {
        clazzInfo.pTessellationState = nullptr;
        env->DeleteLocalRef(pTessellationStateArray);
        return;
    }
    auto count = static_cast<std::size_t>(size);
    auto *copy = awake::VulkanAccessArena::current().allocArray<VkPipelineTessellationStateCreateInfo>(count);
    for (int i = 0; i < size; ++i) {
        auto element = (jobject) env->GetObjectArrayElement(pTessellationStateArray, i);
        VkPipelineTessellationStateCreateInfoAccessor accessor(env, element);
        accessor.fromObject(copy[i]);
        env->DeleteLocalRef(element);
    }
    clazzInfo.pTessellationState = copy;
    env->DeleteLocalRef(pTessellationStateArray);
}

void
VkGraphicsPipelineCreateInfoAccessor::getpRasterizationState(
        VkGraphicsPipelineCreateInfo &clazzInfo) {
    auto pRasterizationStateArray = (jobjectArray) env->GetObjectField(obj,
                                                                       pRasterizationStateField);
    if (pRasterizationStateArray == nullptr) {
        clazzInfo.pRasterizationState = nullptr;
        return;
    }
    auto size = env->GetArrayLength(pRasterizationStateArray);
    if (size <= 0) {
        clazzInfo.pRasterizationState = nullptr;
        env->DeleteLocalRef(pRasterizationStateArray);
        return;
    }
    auto count = static_cast<std::size_t>(size);
    auto *copy = awake::VulkanAccessArena::current().allocArray<VkPipelineRasterizationStateCreateInfo>(count);
    for (int i = 0; i < size; ++i) {
        auto element = (jobject) env->GetObjectArrayElement(pRasterizationStateArray, i);
        VkPipelineRasterizationStateCreateInfoAccessor accessor(env, element);
        accessor.fromObject(copy[i]);
        env->DeleteLocalRef(element);
    }
    clazzInfo.pRasterizationState = copy;
    env->DeleteLocalRef(pRasterizationStateArray);
}

void
VkGraphicsPipelineCreateInfoAccessor::getpDepthStencilState(
        VkGraphicsPipelineCreateInfo &clazzInfo) {
    auto pDepthStencilStateArray = (jobjectArray) env->GetObjectField(obj, pDepthStencilStateField);
    if (pDepthStencilStateArray == nullptr) {
        clazzInfo.pDepthStencilState = nullptr;
        return;
    }
    auto size = env->GetArrayLength(pDepthStencilStateArray);
    if (size <= 0) {
        clazzInfo.pDepthStencilState = nullptr;
        env->DeleteLocalRef(pDepthStencilStateArray);
        return;
    }
    auto count = static_cast<std::size_t>(size);
    auto *copy = awake::VulkanAccessArena::current().allocArray<VkPipelineDepthStencilStateCreateInfo>(count);
    for (int i = 0; i < size; ++i) {
        auto element = (jobject) env->GetObjectArrayElement(pDepthStencilStateArray, i);
        VkPipelineDepthStencilStateCreateInfoAccessor accessor(env, element);
        accessor.fromObject(copy[i]);
        env->DeleteLocalRef(element);
    }
    clazzInfo.pDepthStencilState = copy;
    env->DeleteLocalRef(pDepthStencilStateArray);
}

uint32_t
VkGraphicsPipelineCreateInfoAccessor::getsubpass() {
    return (uint32_t) (jint) env->GetIntField(obj, subpassField); // primitive
}

VkRenderPass
VkGraphicsPipelineCreateInfoAccessor::getrenderPass() {
    auto value = env->GetLongField(obj, renderPassField);
    if (value == 0) {
        return VK_NULL_HANDLE;
    }
    return reinterpret_cast<VkRenderPass>(value); // VkHandle
}

uint32_t
VkGraphicsPipelineCreateInfoAccessor::getbasePipelineIndex() {
    return (uint32_t) (jint) env->GetIntField(obj, basePipelineIndexField); // primitive
}

VkStructureType
VkGraphicsPipelineCreateInfoAccessor::getsType() {
    auto sTypeEnum = (jobject) env->GetObjectField(obj, sTypeField);
    auto enumValue = (VkStructureType) enum_utils::getEnumFromObject(env, sTypeEnum);
    env->DeleteLocalRef(sTypeEnum); // release enum reference
    return enumValue;
}

uint32_t
VkGraphicsPipelineCreateInfoAccessor::getflags() {
    return (uint32_t) (jint) env->GetIntField(obj, flagsField); // primitive
}

void
VkGraphicsPipelineCreateInfoAccessor::getpViewportState(VkGraphicsPipelineCreateInfo &clazzInfo) {
    auto pViewportStateArray = (jobjectArray) env->GetObjectField(obj, pViewportStateField);
    if (pViewportStateArray == nullptr) {
        clazzInfo.pViewportState = nullptr;
        return;
    }
    auto size = env->GetArrayLength(pViewportStateArray);
    if (size <= 0) {
        clazzInfo.pViewportState = nullptr;
        env->DeleteLocalRef(pViewportStateArray);
        return;
    }
    auto count = static_cast<std::size_t>(size);
    auto *copy = awake::VulkanAccessArena::current().allocArray<VkPipelineViewportStateCreateInfo>(count);
    for (int i = 0; i < size; ++i) {
        auto element = (jobject) env->GetObjectArrayElement(pViewportStateArray, i);
        VkPipelineViewportStateCreateInfoAccessor accessor(env, element);
        accessor.fromObject(copy[i]);
        env->DeleteLocalRef(element);
    }
    clazzInfo.pViewportState = copy;
    env->DeleteLocalRef(pViewportStateArray);
}

void
VkGraphicsPipelineCreateInfoAccessor::getpInputAssemblyState(
        VkGraphicsPipelineCreateInfo &clazzInfo) {
    auto pInputAssemblyStateArray = (jobjectArray) env->GetObjectField(obj,
                                                                       pInputAssemblyStateField);
    if (pInputAssemblyStateArray == nullptr) {
        clazzInfo.pInputAssemblyState = nullptr;
        return;
    }
    auto size = env->GetArrayLength(pInputAssemblyStateArray);
    if (size <= 0) {
        clazzInfo.pInputAssemblyState = nullptr;
        env->DeleteLocalRef(pInputAssemblyStateArray);
        return;
    }
    auto count = static_cast<std::size_t>(size);
    auto *copy = awake::VulkanAccessArena::current().allocArray<VkPipelineInputAssemblyStateCreateInfo>(count);
    for (int i = 0; i < size; ++i) {
        auto element = (jobject) env->GetObjectArrayElement(pInputAssemblyStateArray, i);
        VkPipelineInputAssemblyStateCreateInfoAccessor accessor(env, element);
        accessor.fromObject(copy[i]);
        env->DeleteLocalRef(element);
    }
    clazzInfo.pInputAssemblyState = copy;
    env->DeleteLocalRef(pInputAssemblyStateArray);
}

void
VkGraphicsPipelineCreateInfoAccessor::getpColorBlendState(VkGraphicsPipelineCreateInfo &clazzInfo) {
    auto pColorBlendStateArray = (jobjectArray) env->GetObjectField(obj, pColorBlendStateField);
    if (pColorBlendStateArray == nullptr) {
        clazzInfo.pColorBlendState = nullptr;
        return;
    }
    auto size = env->GetArrayLength(pColorBlendStateArray);
    if (size <= 0) {
        clazzInfo.pColorBlendState = nullptr;
        env->DeleteLocalRef(pColorBlendStateArray);
        return;
    }
    auto count = static_cast<std::size_t>(size);
    auto *copy = awake::VulkanAccessArena::current().allocArray<VkPipelineColorBlendStateCreateInfo>(count);
    for (int i = 0; i < size; ++i) {
        auto element = (jobject) env->GetObjectArrayElement(pColorBlendStateArray, i);
        VkPipelineColorBlendStateCreateInfoAccessor accessor(env, element);
        accessor.fromObject(copy[i]);
        env->DeleteLocalRef(element);
    }
    clazzInfo.pColorBlendState = copy;
    env->DeleteLocalRef(pColorBlendStateArray);
}

VkPipelineLayout
VkGraphicsPipelineCreateInfoAccessor::getlayout() {
    auto value = env->GetLongField(obj, layoutField);
    if (value == 0) {
        return VK_NULL_HANDLE;
    }
    return reinterpret_cast<VkPipelineLayout>(value); // VkHandle
}

void
VkGraphicsPipelineCreateInfoAccessor::getpNext(VkGraphicsPipelineCreateInfo &clazzInfo) {
    auto ref = (void *) (jobject) env->GetObjectField(obj, pNextField); // Any Object
    clazzInfo.pNext = ref;
}

void
VkGraphicsPipelineCreateInfoAccessor::fromObject(VkGraphicsPipelineCreateInfo &clazzInfo) {
    clazzInfo.sType = getsType(); // Enum VkStructureType
    getpNext(clazzInfo); // Other void*
    clazzInfo.flags = getflags(); // Primitive uint32_t
    getpStages(clazzInfo);  // VkPipelineShaderStageCreateInfo Object Array
    getpVertexInputState(clazzInfo);  // VkPipelineVertexInputStateCreateInfo Object Array
    getpInputAssemblyState(clazzInfo);  // VkPipelineInputAssemblyStateCreateInfo Object Array
    getpTessellationState(clazzInfo);  // VkPipelineTessellationStateCreateInfo Object Array
    getpViewportState(clazzInfo);  // VkPipelineViewportStateCreateInfo Object Array
    getpRasterizationState(clazzInfo);  // VkPipelineRasterizationStateCreateInfo Object Array
    getpMultisampleState(clazzInfo);  // VkPipelineMultisampleStateCreateInfo Object Array
    getpDepthStencilState(clazzInfo);  // VkPipelineDepthStencilStateCreateInfo Object Array
    getpColorBlendState(clazzInfo);  // VkPipelineColorBlendStateCreateInfo Object Array
    getpDynamicState(clazzInfo);  // VkPipelineDynamicStateCreateInfo Object Array
    clazzInfo.layout = getlayout(); // VkHandle
    clazzInfo.renderPass = getrenderPass(); // VkHandle
    clazzInfo.subpass = getsubpass(); // Primitive uint32_t
    clazzInfo.basePipelineHandle = getbasePipelineHandle(); // VkHandle
    clazzInfo.basePipelineIndex = getbasePipelineIndex(); // Primitive uint32_t
}

void
VkGraphicsPipelineCreateInfoAccessor::getpVertexInputState(
        VkGraphicsPipelineCreateInfo &clazzInfo) {
    auto pVertexInputStateArray = (jobjectArray) env->GetObjectField(obj, pVertexInputStateField);
    if (pVertexInputStateArray == nullptr) {
        clazzInfo.pVertexInputState = nullptr;
        return;
    }
    auto size = env->GetArrayLength(pVertexInputStateArray);
    if (size <= 0) {
        clazzInfo.pVertexInputState = nullptr;
        env->DeleteLocalRef(pVertexInputStateArray);
        return;
    }
    auto count = static_cast<std::size_t>(size);
    auto *copy = awake::VulkanAccessArena::current().allocArray<VkPipelineVertexInputStateCreateInfo>(count);
    for (int i = 0; i < size; ++i) {
        auto element = (jobject) env->GetObjectArrayElement(pVertexInputStateArray, i);
        VkPipelineVertexInputStateCreateInfoAccessor accessor(env, element);
        accessor.fromObject(copy[i]);
        env->DeleteLocalRef(element);
    }
    clazzInfo.pVertexInputState = copy;
    env->DeleteLocalRef(pVertexInputStateArray);
}

void
VkGraphicsPipelineCreateInfoAccessor::getpDynamicState(VkGraphicsPipelineCreateInfo &clazzInfo) {
    auto pDynamicStateArray = (jobjectArray) env->GetObjectField(obj, pDynamicStateField);
    if (pDynamicStateArray == nullptr) {
        clazzInfo.pDynamicState = nullptr;
        return;
    }
    auto size = env->GetArrayLength(pDynamicStateArray);
    if (size <= 0) {
        clazzInfo.pDynamicState = nullptr;
        env->DeleteLocalRef(pDynamicStateArray);
        return;
    }
    auto count = static_cast<std::size_t>(size);
    auto *copy = awake::VulkanAccessArena::current().allocArray<VkPipelineDynamicStateCreateInfo>(count);
    for (int i = 0; i < size; ++i) {
        auto element = (jobject) env->GetObjectArrayElement(pDynamicStateArray, i);
        VkPipelineDynamicStateCreateInfoAccessor accessor(env, element);
        accessor.fromObject(copy[i]);
        env->DeleteLocalRef(element);
    }
    clazzInfo.pDynamicState = copy;
    env->DeleteLocalRef(pDynamicStateArray);
}

void
VkGraphicsPipelineCreateInfoAccessor::getpMultisampleState(
        VkGraphicsPipelineCreateInfo &clazzInfo) {
    auto pMultisampleStateArray = (jobjectArray) env->GetObjectField(obj, pMultisampleStateField);
    if (pMultisampleStateArray == nullptr) {
        clazzInfo.pMultisampleState = nullptr;
        return;
    }
    auto size = env->GetArrayLength(pMultisampleStateArray);
    if (size <= 0) {
        clazzInfo.pMultisampleState = nullptr;
        env->DeleteLocalRef(pMultisampleStateArray);
        return;
    }
    auto count = static_cast<std::size_t>(size);
    auto *copy = awake::VulkanAccessArena::current().allocArray<VkPipelineMultisampleStateCreateInfo>(count);
    for (int i = 0; i < size; ++i) {
        auto element = (jobject) env->GetObjectArrayElement(pMultisampleStateArray, i);
        VkPipelineMultisampleStateCreateInfoAccessor accessor(env, element);
        accessor.fromObject(copy[i]);
        env->DeleteLocalRef(element);
    }
    clazzInfo.pMultisampleState = copy;
    env->DeleteLocalRef(pMultisampleStateArray);
}

VkGraphicsPipelineCreateInfoAccessor::~VkGraphicsPipelineCreateInfoAccessor() {
    env->DeleteLocalRef(clazz);
}


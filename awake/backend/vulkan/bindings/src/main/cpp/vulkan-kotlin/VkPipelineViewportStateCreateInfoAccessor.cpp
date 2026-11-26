/*
 *  VkPipelineViewportStateCreateInfoAccessor.cpp
 *  Vulkan accessor for VkPipelineViewportStateCreateInfo
 *  Created by Ron June Valdoz */

#include <includes/VkPipelineViewportStateCreateInfoAccessor.h>
#include <includes/VulkanAccessArena.h>

VkPipelineViewportStateCreateInfoAccessor::VkPipelineViewportStateCreateInfoAccessor(JNIEnv *env,
                                                                                     jobject obj)
        : env(env), obj(obj) {
    clazz = env->GetObjectClass(obj);
    sTypeField = env->GetFieldID(clazz, "sType",
                                 "Lcom/awakekt/awake/vulkan/enums/VkStructureType;");
    pNextField = env->GetFieldID(clazz, "pNext", "Ljava/lang/Object;");
    flagsField = env->GetFieldID(clazz, "flags", "I");
    pViewportsField = env->GetFieldID(clazz, "pViewports",
                                      "[Lcom/awakekt/awake/vulkan/models/VkViewport;");
    pScissorsField = env->GetFieldID(clazz, "pScissors",
                                     "[Lcom/awakekt/awake/vulkan/models/VkRect2D;");
}

VkStructureType
VkPipelineViewportStateCreateInfoAccessor::getsType() {
    auto sTypeEnum = (jobject) env->GetObjectField(obj, sTypeField);
    auto enumValue = (VkStructureType) enum_utils::getEnumFromObject(env, sTypeEnum);
    env->DeleteLocalRef(sTypeEnum); // release enum reference
    return enumValue;
}

uint32_t
VkPipelineViewportStateCreateInfoAccessor::getflags() {
    return (uint32_t) (jint) env->GetIntField(obj, flagsField); // primitive
}

void
VkPipelineViewportStateCreateInfoAccessor::getpNext(VkPipelineViewportStateCreateInfo &clazzInfo) {
    auto ref = (void *) (jobject) env->GetObjectField(obj, pNextField); // Any Object
    clazzInfo.pNext = ref;
}

void
VkPipelineViewportStateCreateInfoAccessor::fromObject(
        VkPipelineViewportStateCreateInfo &clazzInfo) {
    clazzInfo.sType = getsType(); // Enum VkStructureType
    getpNext(clazzInfo); // Other void*
    clazzInfo.flags = getflags(); // Primitive uint32_t
    getpViewports(clazzInfo);  // VkViewport Object Array
    getpScissors(clazzInfo);  // VkRect2D Object Array
}

void
VkPipelineViewportStateCreateInfoAccessor::getpViewports(
        VkPipelineViewportStateCreateInfo &clazzInfo) {
    auto pViewportsArray = (jobjectArray) env->GetObjectField(obj, pViewportsField);
    if (pViewportsArray == nullptr) {
        clazzInfo.viewportCount = 0;
        clazzInfo.pViewports = nullptr;
        return;
    }
    auto size = env->GetArrayLength(pViewportsArray);
    if (size <= 0) {
        clazzInfo.viewportCount = 0;
        clazzInfo.pViewports = nullptr;
        env->DeleteLocalRef(pViewportsArray);
        return;
    }
    auto count = static_cast<std::size_t>(size);
    auto *copy = awake::VulkanAccessArena::current().allocArray<VkViewport>(count);
    for (int i = 0; i < size; ++i) {
        auto element = (jobject) env->GetObjectArrayElement(pViewportsArray, i);
        VkViewportAccessor accessor(env, element);
        accessor.fromObject(copy[i]);
        env->DeleteLocalRef(element);
    }
    clazzInfo.viewportCount = static_cast<uint32_t>(count);
    clazzInfo.pViewports = copy;
    env->DeleteLocalRef(pViewportsArray);
}

void
VkPipelineViewportStateCreateInfoAccessor::getpScissors(
        VkPipelineViewportStateCreateInfo &clazzInfo) {
    auto pScissorsArray = (jobjectArray) env->GetObjectField(obj, pScissorsField);
    if (pScissorsArray == nullptr) {
        clazzInfo.scissorCount = 0;
        clazzInfo.pScissors = nullptr;
        return;
    }
    auto size = env->GetArrayLength(pScissorsArray);
    if (size <= 0) {
        clazzInfo.scissorCount = 0;
        clazzInfo.pScissors = nullptr;
        env->DeleteLocalRef(pScissorsArray);
        return;
    }
    auto count = static_cast<std::size_t>(size);
    auto *copy = awake::VulkanAccessArena::current().allocArray<VkRect2D>(count);
    for (int i = 0; i < size; ++i) {
        auto element = (jobject) env->GetObjectArrayElement(pScissorsArray, i);
        VkRect2DAccessor accessor(env, element);
        accessor.fromObject(copy[i]);
        env->DeleteLocalRef(element);
    }
    clazzInfo.scissorCount = static_cast<uint32_t>(count);
    clazzInfo.pScissors = copy;
    env->DeleteLocalRef(pScissorsArray);
}

VkPipelineViewportStateCreateInfoAccessor::~VkPipelineViewportStateCreateInfoAccessor() {
    env->DeleteLocalRef(clazz);
}


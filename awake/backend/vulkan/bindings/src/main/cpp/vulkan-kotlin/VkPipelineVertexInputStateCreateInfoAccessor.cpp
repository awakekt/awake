/*
 *  VkPipelineVertexInputStateCreateInfoAccessor.cpp
 *  Vulkan accessor for VkPipelineVertexInputStateCreateInfo
 *  Created by Ron June Valdoz */

#include <includes/VkPipelineVertexInputStateCreateInfoAccessor.h>
#include <includes/VulkanAccessArena.h>

VkPipelineVertexInputStateCreateInfoAccessor::VkPipelineVertexInputStateCreateInfoAccessor(
        JNIEnv *env, jobject obj) : env(env), obj(obj) {
    clazz = env->GetObjectClass(obj);
    sTypeField = env->GetFieldID(clazz, "sType",
                                 "Lcom/awakekt/awake/vulkan/enums/VkStructureType;");
    pNextField = env->GetFieldID(clazz, "pNext", "Ljava/lang/Object;");
    flagsField = env->GetFieldID(clazz, "flags", "I");
    pVertexBindingDescriptionsField = env->GetFieldID(clazz, "pVertexBindingDescriptions",
                                                      "[Lcom/awakekt/awake/vulkan/models/info/pipeline/VkVertexInputBindingDescription;");
    pVertexAttributeDescriptionsField = env->GetFieldID(clazz, "pVertexAttributeDescriptions",
                                                        "[Lcom/awakekt/awake/vulkan/models/info/pipeline/VkVertexInputAttributeDescription;");
}

VkStructureType
VkPipelineVertexInputStateCreateInfoAccessor::getsType() {
    auto sTypeEnum = (jobject) env->GetObjectField(obj, sTypeField);
    auto enumValue = (VkStructureType) enum_utils::getEnumFromObject(env, sTypeEnum);
    env->DeleteLocalRef(sTypeEnum); // release enum reference
    return enumValue;
}

uint32_t
VkPipelineVertexInputStateCreateInfoAccessor::getflags() {
    return (uint32_t) (jint) env->GetIntField(obj, flagsField); // primitive
}

void
VkPipelineVertexInputStateCreateInfoAccessor::fromObject(
        VkPipelineVertexInputStateCreateInfo &clazzInfo) {
    clazzInfo.sType = getsType(); // Enum VkStructureType
    getpNext(clazzInfo); // Other void*
    clazzInfo.flags = getflags(); // Primitive uint32_t
    getpVertexBindingDescriptions(clazzInfo);  // VkVertexInputBindingDescription Object Array
    getpVertexAttributeDescriptions(clazzInfo);  // VkVertexInputAttributeDescription Object Array
}

void
VkPipelineVertexInputStateCreateInfoAccessor::getpNext(
        VkPipelineVertexInputStateCreateInfo &clazzInfo) {
    auto ref = (void *) (jobject) env->GetObjectField(obj, pNextField); // Any Object
    clazzInfo.pNext = ref;
}

void
VkPipelineVertexInputStateCreateInfoAccessor::getpVertexAttributeDescriptions(
        VkPipelineVertexInputStateCreateInfo &clazzInfo) {
    auto pVertexAttributeDescriptionsArray = (jobjectArray) env->GetObjectField(obj,
                                                                                pVertexAttributeDescriptionsField);
    if (pVertexAttributeDescriptionsArray == nullptr) {
        clazzInfo.vertexAttributeDescriptionCount = 0;
        clazzInfo.pVertexAttributeDescriptions = nullptr;
        return;
    }
    auto size = env->GetArrayLength(pVertexAttributeDescriptionsArray);
    if (size <= 0) {
        clazzInfo.vertexAttributeDescriptionCount = 0;
        clazzInfo.pVertexAttributeDescriptions = nullptr;
        env->DeleteLocalRef(pVertexAttributeDescriptionsArray);
        return;
    }
    auto count = static_cast<std::size_t>(size);
    auto *copy = awake::VulkanAccessArena::current().allocArray<VkVertexInputAttributeDescription>(count);
    for (int i = 0; i < size; ++i) {
        auto element = (jobject) env->GetObjectArrayElement(pVertexAttributeDescriptionsArray, i);
        VkVertexInputAttributeDescriptionAccessor accessor(env, element);
        accessor.fromObject(copy[i]);
        env->DeleteLocalRef(element);
    }
    clazzInfo.vertexAttributeDescriptionCount = static_cast<uint32_t>(count);
    clazzInfo.pVertexAttributeDescriptions = copy;
    env->DeleteLocalRef(pVertexAttributeDescriptionsArray);
}

void
VkPipelineVertexInputStateCreateInfoAccessor::getpVertexBindingDescriptions(
        VkPipelineVertexInputStateCreateInfo &clazzInfo) {
    auto pVertexBindingDescriptionsArray = (jobjectArray) env->GetObjectField(obj,
                                                                              pVertexBindingDescriptionsField);
    if (pVertexBindingDescriptionsArray == nullptr) {
        clazzInfo.vertexBindingDescriptionCount = 0;
        clazzInfo.pVertexBindingDescriptions = nullptr;
        return;
    }
    auto size = env->GetArrayLength(pVertexBindingDescriptionsArray);
    if (size <= 0) {
        clazzInfo.vertexBindingDescriptionCount = 0;
        clazzInfo.pVertexBindingDescriptions = nullptr;
        env->DeleteLocalRef(pVertexBindingDescriptionsArray);
        return;
    }
    auto count = static_cast<std::size_t>(size);
    auto *copy = awake::VulkanAccessArena::current().allocArray<VkVertexInputBindingDescription>(count);
    for (int i = 0; i < size; ++i) {
        auto element = (jobject) env->GetObjectArrayElement(pVertexBindingDescriptionsArray, i);
        VkVertexInputBindingDescriptionAccessor accessor(env, element);
        accessor.fromObject(copy[i]);
        env->DeleteLocalRef(element);
    }
    clazzInfo.vertexBindingDescriptionCount = static_cast<uint32_t>(count);
    clazzInfo.pVertexBindingDescriptions = copy;
    env->DeleteLocalRef(pVertexBindingDescriptionsArray);
}

VkPipelineVertexInputStateCreateInfoAccessor::~VkPipelineVertexInputStateCreateInfoAccessor() {
    env->DeleteLocalRef(clazz);
}


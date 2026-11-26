/*
 *  VkPipelineLayoutCreateInfoAccessor.cpp
 *  Vulkan accessor for VkPipelineLayoutCreateInfo
 *  Created by Ron June Valdoz */

#include <includes/VkPipelineLayoutCreateInfoAccessor.h>
#include <includes/VulkanAccessArena.h>

VkPipelineLayoutCreateInfoAccessor::VkPipelineLayoutCreateInfoAccessor(JNIEnv *env, jobject obj)
        : env(env), obj(obj) {
    clazz = env->GetObjectClass(obj);
    sTypeField = env->GetFieldID(clazz, "sType",
                                 "Lcom/awakekt/awake/vulkan/enums/VkStructureType;");
    pNextField = env->GetFieldID(clazz, "pNext", "Ljava/lang/Object;");
    flagsField = env->GetFieldID(clazz, "flags", "I");
    pSetLayoutsField = env->GetFieldID(clazz, "pSetLayouts", "[Ljava/lang/Long;");
    pushConstantRangeCountField = env->GetFieldID(clazz, "pushConstantRangeCount", "I");
    pPushConstantRangesField = env->GetFieldID(clazz, "pPushConstantRanges",
                                               "[Lcom/awakekt/awake/vulkan/models/info/pipeline/VkPushConstantRange;");
}

VkStructureType
VkPipelineLayoutCreateInfoAccessor::getsType() {
    auto sTypeEnum = (jobject) env->GetObjectField(obj, sTypeField);
    auto enumValue = (VkStructureType) enum_utils::getEnumFromObject(env, sTypeEnum);
    env->DeleteLocalRef(sTypeEnum); // release enum reference
    return enumValue;
}

uint32_t
VkPipelineLayoutCreateInfoAccessor::getflags() {
    return (uint32_t) (jint) env->GetIntField(obj, flagsField); // primitive
}

uint32_t
VkPipelineLayoutCreateInfoAccessor::getpushConstantRangeCount() {
    return (uint32_t) (jint) env->GetIntField(obj, pushConstantRangeCountField); // primitive
}

void
VkPipelineLayoutCreateInfoAccessor::getpPushConstantRanges(VkPipelineLayoutCreateInfo &clazzInfo) {
    auto pPushConstantRangesArray = (jobjectArray) env->GetObjectField(obj,
                                                                       pPushConstantRangesField);
    if (pPushConstantRangesArray == nullptr) {
        clazzInfo.pPushConstantRanges = nullptr;
        return;
    }
    auto size = env->GetArrayLength(pPushConstantRangesArray);
    if (size <= 0) {
        clazzInfo.pPushConstantRanges = nullptr;
        env->DeleteLocalRef(pPushConstantRangesArray);
        return;
    }
    auto count = static_cast<std::size_t>(size);
    auto *copy = awake::VulkanAccessArena::current().allocArray<VkPushConstantRange>(count);
    for (int i = 0; i < size; ++i) {
        auto element = (jobject) env->GetObjectArrayElement(pPushConstantRangesArray, i);
        VkPushConstantRangeAccessor accessor(env, element);
        accessor.fromObject(copy[i]);
        env->DeleteLocalRef(element);
    }
    clazzInfo.pPushConstantRanges = copy;
    env->DeleteLocalRef(pPushConstantRangesArray);
}

void
VkPipelineLayoutCreateInfoAccessor::fromObject(VkPipelineLayoutCreateInfo &clazzInfo) {
    clazzInfo.sType = getsType(); // Enum VkStructureType
    getpNext(clazzInfo); // Other void*
    clazzInfo.flags = getflags(); // Primitive uint32_t
    getpSetLayouts(clazzInfo);  // Long Object Array
    clazzInfo.pushConstantRangeCount = getpushConstantRangeCount(); // Primitive uint32_t
    getpPushConstantRanges(clazzInfo);  // VkPushConstantRange Object Array
}

void
VkPipelineLayoutCreateInfoAccessor::getpNext(VkPipelineLayoutCreateInfo &clazzInfo) {
    auto ref = (void *) (jobject) env->GetObjectField(obj, pNextField); // Any Object
    clazzInfo.pNext = ref;
}

void
VkPipelineLayoutCreateInfoAccessor::getpSetLayouts(VkPipelineLayoutCreateInfo &clazzInfo) {
    auto pSetLayoutsArray = (jobjectArray) env->GetObjectField(obj, pSetLayoutsField);
    if (pSetLayoutsArray == nullptr) {
        clazzInfo.setLayoutCount = 0;
        clazzInfo.pSetLayouts = nullptr;
        return;
    }
    auto size = env->GetArrayLength(pSetLayoutsArray);
    if (size <= 0) {
        clazzInfo.setLayoutCount = 0;
        clazzInfo.pSetLayouts = nullptr;
        env->DeleteLocalRef(pSetLayoutsArray);
        return;
    }
    auto count = static_cast<std::size_t>(size);
    auto *copy = awake::VulkanAccessArena::current().allocArray<VkDescriptorSetLayout>(count);
    for (int i = 0; i < size; ++i) {
        auto element = env->GetObjectArrayElement(pSetLayoutsArray, i);
        jclass elementClass = env->GetObjectClass(element);
        jmethodID getValueMethod = env->GetMethodID(elementClass, "longValue", "()J");
        jlong value = env->CallLongMethod(element, getValueMethod);
        copy[i] = reinterpret_cast<VkDescriptorSetLayout>(value);
        env->DeleteLocalRef(elementClass);
        env->DeleteLocalRef(element);
    }
    clazzInfo.setLayoutCount = static_cast<uint32_t>(count);
    clazzInfo.pSetLayouts = copy;
    env->DeleteLocalRef(pSetLayoutsArray);
}

VkPipelineLayoutCreateInfoAccessor::~VkPipelineLayoutCreateInfoAccessor() {
    env->DeleteLocalRef(clazz);
}


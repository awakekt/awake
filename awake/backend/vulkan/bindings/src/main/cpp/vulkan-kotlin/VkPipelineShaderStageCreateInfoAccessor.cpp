/*
 *  VkPipelineShaderStageCreateInfoAccessor.cpp
 *  Vulkan accessor for VkPipelineShaderStageCreateInfo
 *  Created by Ron June Valdoz */

#include <includes/VkPipelineShaderStageCreateInfoAccessor.h>
#include <includes/VulkanAccessArena.h>

VkPipelineShaderStageCreateInfoAccessor::VkPipelineShaderStageCreateInfoAccessor(JNIEnv *env,
                                                                                 jobject obj) : env(
        env), obj(obj) {
    clazz = env->GetObjectClass(obj);
    sTypeField = env->GetFieldID(clazz, "sType",
                                 "Lcom/awakekt/awake/vulkan/enums/VkStructureType;");
    pNextField = env->GetFieldID(clazz, "pNext", "Ljava/lang/Object;");
    flagsField = env->GetFieldID(clazz, "flags", "I");
    stageField = env->GetFieldID(clazz, "stage",
                                 "Lcom/awakekt/awake/vulkan/enums/VkShaderStageFlagBits;");
    moduleField = env->GetFieldID(clazz, "module", "J");
    pNameField = env->GetFieldID(clazz, "pName", "Ljava/lang/String;");
    pSpecializationInfoField = env->GetFieldID(clazz, "pSpecializationInfo",
                                               "[Lcom/awakekt/awake/vulkan/models/info/pipeline/VkSpecializationInfo;");
}

VkStructureType
VkPipelineShaderStageCreateInfoAccessor::getsType() {
    auto sTypeEnum = (jobject) env->GetObjectField(obj, sTypeField);
    auto enumValue = (VkStructureType) enum_utils::getEnumFromObject(env, sTypeEnum);
    env->DeleteLocalRef(sTypeEnum); // release enum reference
    return enumValue;
}

uint32_t
VkPipelineShaderStageCreateInfoAccessor::getflags() {
    return (uint32_t) (jint) env->GetIntField(obj, flagsField); // primitive
}

void
VkPipelineShaderStageCreateInfoAccessor::getpSpecializationInfo(
        VkPipelineShaderStageCreateInfo &clazzInfo) {
    auto pSpecializationInfoArray = (jobjectArray) env->GetObjectField(obj,
                                                                       pSpecializationInfoField);
    if (pSpecializationInfoArray == nullptr) {
        clazzInfo.pSpecializationInfo = nullptr;
        return;
    }
    auto size = env->GetArrayLength(pSpecializationInfoArray);
    if (size <= 0) {
        clazzInfo.pSpecializationInfo = nullptr;
        env->DeleteLocalRef(pSpecializationInfoArray);
        return;
    }
    auto count = static_cast<std::size_t>(size);
    auto *copy = awake::VulkanAccessArena::current().allocArray<VkSpecializationInfo>(count);
    for (int i = 0; i < size; ++i) {
        auto element = (jobject) env->GetObjectArrayElement(pSpecializationInfoArray, i);
        VkSpecializationInfoAccessor accessor(env, element);
        accessor.fromObject(copy[i]);
        env->DeleteLocalRef(element);
    }
    clazzInfo.pSpecializationInfo = copy;
    env->DeleteLocalRef(pSpecializationInfoArray);
}

void
VkPipelineShaderStageCreateInfoAccessor::getpName(VkPipelineShaderStageCreateInfo &clazzInfo) {
    auto pNameObj = (jstring) env->GetObjectField(obj, pNameField);
    clazzInfo.pName = awake::VulkanAccessArena::current().copyJString(env, pNameObj);
    if (pNameObj != nullptr) {
        env->DeleteLocalRef(pNameObj);
    }
}

VkShaderModule
VkPipelineShaderStageCreateInfoAccessor::getmodule() {
    auto value = env->GetLongField(obj, moduleField);
    if (value == 0) {
        return VK_NULL_HANDLE;
    }
    return reinterpret_cast<VkShaderModule>(value); // VkHandle
}

void
VkPipelineShaderStageCreateInfoAccessor::fromObject(VkPipelineShaderStageCreateInfo &clazzInfo) {
    clazzInfo.sType = getsType(); // Enum VkStructureType
    getpNext(clazzInfo); // Other void*
    clazzInfo.flags = getflags(); // Primitive uint32_t
    clazzInfo.stage = getstage(); // Enum VkShaderStageFlagBits
    clazzInfo.module = getmodule(); // VkHandle
    getpName(clazzInfo); // Other const char*
    getpSpecializationInfo(clazzInfo);  // VkSpecializationInfo Object Array
}

void
VkPipelineShaderStageCreateInfoAccessor::getpNext(VkPipelineShaderStageCreateInfo &clazzInfo) {
    auto ref = (void *) (jobject) env->GetObjectField(obj, pNextField); // Any Object
    clazzInfo.pNext = ref;
}

VkShaderStageFlagBits
VkPipelineShaderStageCreateInfoAccessor::getstage() {
    auto stageEnum = (jobject) env->GetObjectField(obj, stageField);
    auto enumValue = (VkShaderStageFlagBits) enum_utils::getEnumFromObject(env, stageEnum);
    env->DeleteLocalRef(stageEnum); // release enum reference
    return enumValue;
}

VkPipelineShaderStageCreateInfoAccessor::~VkPipelineShaderStageCreateInfoAccessor() {
    env->DeleteLocalRef(clazz);
}


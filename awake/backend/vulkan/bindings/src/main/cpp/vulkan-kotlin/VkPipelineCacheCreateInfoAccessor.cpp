/*
 *  VkPipelineCacheCreateInfoAccessor.cpp
 *  Vulkan accessor for VkPipelineCacheCreateInfo
 *  Created by Ron June Valdoz */

#include <includes/VkPipelineCacheCreateInfoAccessor.h>
#include <includes/VulkanAccessArena.h>

VkPipelineCacheCreateInfoAccessor::VkPipelineCacheCreateInfoAccessor(JNIEnv *env, jobject obj)
        : env(env), obj(obj) {
    clazz = env->GetObjectClass(obj);
    sTypeField = env->GetFieldID(clazz, "sType",
                                 "Lcom/awakekt/awake/vulkan/enums/VkStructureType;");
    pNextField = env->GetFieldID(clazz, "pNext", "Ljava/lang/Object;");
    flagsField = env->GetFieldID(clazz, "flags", "I");
    pInitialDataField = env->GetFieldID(clazz, "pInitialData", "[Ljava/lang/Object;");
}

VkStructureType
VkPipelineCacheCreateInfoAccessor::getsType() {
    auto sTypeEnum = (jobject) env->GetObjectField(obj, sTypeField);
    auto enumValue = (VkStructureType) enum_utils::getEnumFromObject(env, sTypeEnum);
    env->DeleteLocalRef(sTypeEnum); // release enum reference
    return enumValue;
}

uint32_t
VkPipelineCacheCreateInfoAccessor::getflags() {
    return (uint32_t) (jint) env->GetIntField(obj, flagsField); // primitive
}

void
VkPipelineCacheCreateInfoAccessor::fromObject(VkPipelineCacheCreateInfo &clazzInfo) {
    clazzInfo.sType = getsType(); // Enum VkStructureType
    getpNext(clazzInfo); // Other void*
    clazzInfo.flags = getflags(); // Primitive uint32_t
    getpInitialData(clazzInfo);  // Object Object Array
}

void
VkPipelineCacheCreateInfoAccessor::getpInitialData(VkPipelineCacheCreateInfo &clazzInfo) {
    auto pInitialDataArray = (jobjectArray) env->GetObjectField(obj, pInitialDataField);
    if (pInitialDataArray == nullptr) {
        clazzInfo.initialDataSize = 0;
        clazzInfo.pInitialData = nullptr;
        return;
    }
    auto size = env->GetArrayLength(pInitialDataArray);
    if (size <= 0) {
        clazzInfo.initialDataSize = 0;
        clazzInfo.pInitialData = nullptr;
        env->DeleteLocalRef(pInitialDataArray);
        return;
    }
    auto count = static_cast<std::size_t>(size);
    auto *copy = awake::VulkanAccessArena::current().allocArray<const void *>(count);
    for (int i = 0; i < size; ++i) {
        auto element = (jobject) env->GetObjectArrayElement(pInitialDataArray, i);
        copy[i] = element;
        env->DeleteLocalRef(element);
    }
    clazzInfo.initialDataSize = count;
    clazzInfo.pInitialData = copy;
    env->DeleteLocalRef(pInitialDataArray);
}

void
VkPipelineCacheCreateInfoAccessor::getpNext(VkPipelineCacheCreateInfo &clazzInfo) {
    auto ref = (void *) (jobject) env->GetObjectField(obj, pNextField); // Any Object
    clazzInfo.pNext = ref;
}

VkPipelineCacheCreateInfoAccessor::~VkPipelineCacheCreateInfoAccessor() {
    env->DeleteLocalRef(clazz);
}


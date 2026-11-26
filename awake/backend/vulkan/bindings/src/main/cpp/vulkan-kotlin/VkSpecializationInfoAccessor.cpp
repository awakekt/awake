/*
 *  VkSpecializationInfoAccessor.cpp
 *  Vulkan accessor for VkSpecializationInfo
 *  Created by Ron June Valdoz */

#include <includes/VkSpecializationInfoAccessor.h>
#include <includes/VulkanAccessArena.h>

VkSpecializationInfoAccessor::VkSpecializationInfoAccessor(JNIEnv *env, jobject obj) : env(env),
                                                                                       obj(obj) {
    clazz = env->GetObjectClass(obj);
    mapEntryCountField = env->GetFieldID(clazz, "mapEntryCount", "I");
    pMapEntriesField = env->GetFieldID(clazz, "pMapEntries",
                                       "[Lcom/awakekt/awake/vulkan/models/info/pipeline/VkSpecializationMapEntry;");
    dataSizeField = env->GetFieldID(clazz, "dataSize", "J");
    pDataField = env->GetFieldID(clazz, "pData", "[Ljava/lang/Object;");
}

uint32_t
VkSpecializationInfoAccessor::getmapEntryCount() {
    return (uint32_t) (jint) env->GetIntField(obj, mapEntryCountField); // primitive
}

void
VkSpecializationInfoAccessor::fromObject(VkSpecializationInfo &clazzInfo) {
    clazzInfo.mapEntryCount = getmapEntryCount(); // Primitive uint32_t
    getpMapEntries(clazzInfo);  // VkSpecializationMapEntry Object Array
    clazzInfo.dataSize = getdataSize(); // Primitive uint64_t
    getpData(clazzInfo);  // Object Object Array
}

uint64_t
VkSpecializationInfoAccessor::getdataSize() {
    return (uint64_t) (jlong) env->GetLongField(obj, dataSizeField); // primitive
}

void
VkSpecializationInfoAccessor::getpMapEntries(VkSpecializationInfo &clazzInfo) {
    auto pMapEntriesArray = (jobjectArray) env->GetObjectField(obj, pMapEntriesField);
    if (pMapEntriesArray == nullptr) {
        clazzInfo.pMapEntries = nullptr;
        return;
    }
    auto size = env->GetArrayLength(pMapEntriesArray);
    if (size <= 0) {
        clazzInfo.pMapEntries = nullptr;
        env->DeleteLocalRef(pMapEntriesArray);
        return;
    }
    auto count = static_cast<std::size_t>(size);
    auto *copy = awake::VulkanAccessArena::current().allocArray<VkSpecializationMapEntry>(count);
    for (int i = 0; i < size; ++i) {
        auto element = (jobject) env->GetObjectArrayElement(pMapEntriesArray, i);
        VkSpecializationMapEntryAccessor accessor(env, element);
        accessor.fromObject(copy[i]);
        env->DeleteLocalRef(element);
    }
    clazzInfo.pMapEntries = copy;
    env->DeleteLocalRef(pMapEntriesArray);
}

void
VkSpecializationInfoAccessor::getpData(VkSpecializationInfo &clazzInfo) {
    auto pDataArray = (jobjectArray) env->GetObjectField(obj, pDataField);
    if (pDataArray == nullptr) {
        clazzInfo.pData = nullptr;
        return;
    }
    auto size = env->GetArrayLength(pDataArray);
    if (size <= 0) {
        clazzInfo.pData = nullptr;
        env->DeleteLocalRef(pDataArray);
        return;
    }
    auto count = static_cast<std::size_t>(size);
    auto *copy = awake::VulkanAccessArena::current().allocArray<const void *>(count);
    for (int i = 0; i < size; ++i) {
        auto element = (jobject) env->GetObjectArrayElement(pDataArray, i);
        copy[i] = element;
        env->DeleteLocalRef(element);
    }
    clazzInfo.pData = copy;
    env->DeleteLocalRef(pDataArray);
}

VkSpecializationInfoAccessor::~VkSpecializationInfoAccessor() {
    env->DeleteLocalRef(clazz);
}


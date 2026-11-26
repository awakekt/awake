/*
 *  VkDeviceQueueCreateInfoAccessor.cpp
 *  Vulkan accessor for VkDeviceQueueCreateInfo
 *  Created by Ron June Valdoz */

#include <includes/VkDeviceQueueCreateInfoAccessor.h>
#include <includes/VulkanAccessArena.h>

VkDeviceQueueCreateInfoAccessor::VkDeviceQueueCreateInfoAccessor(JNIEnv *env, jobject obj) : env(
        env), obj(obj) {
    clazz = env->GetObjectClass(obj);
    sTypeField = env->GetFieldID(clazz, "sType",
                                 "Lcom/awakekt/awake/vulkan/enums/VkStructureType;");
    pNextField = env->GetFieldID(clazz, "pNext", "Ljava/lang/Object;");
    flagsField = env->GetFieldID(clazz, "flags", "I");
    queueFamilyIndexField = env->GetFieldID(clazz, "queueFamilyIndex", "I");
    queueCountField = env->GetFieldID(clazz, "queueCount", "I");
    pQueuePrioritiesField = env->GetFieldID(clazz, "pQueuePriorities", "[F");
}

VkStructureType
VkDeviceQueueCreateInfoAccessor::getsType() {
    auto sTypeEnum = (jobject) env->GetObjectField(obj, sTypeField);
    auto enumValue = (VkStructureType) enum_utils::getEnumFromObject(env, sTypeEnum);
    env->DeleteLocalRef(sTypeEnum); // release enum reference
    return enumValue;
}

uint32_t
VkDeviceQueueCreateInfoAccessor::getqueueFamilyIndex() {
    return (uint32_t) (jint) env->GetIntField(obj, queueFamilyIndexField); // primitive
}

uint32_t
VkDeviceQueueCreateInfoAccessor::getflags() {
    return (uint32_t) (jint) env->GetIntField(obj, flagsField); // primitive
}

void
VkDeviceQueueCreateInfoAccessor::getpQueuePriorities(VkDeviceQueueCreateInfo &clazzInfo) {
    auto pQueuePrioritiesArray = (jfloatArray) env->GetObjectField(obj, pQueuePrioritiesField);
    if (pQueuePrioritiesArray == nullptr) {
        clazzInfo.pQueuePriorities = nullptr;
        return;
    }
    auto size = env->GetArrayLength(pQueuePrioritiesArray);
    if (size <= 0) {
        clazzInfo.pQueuePriorities = nullptr;
        env->DeleteLocalRef(pQueuePrioritiesArray);
        return;
    }
    auto count = static_cast<std::size_t>(size);
    auto *copy = awake::VulkanAccessArena::current().allocArray<float>(count);
    env->GetFloatArrayRegion(pQueuePrioritiesArray, 0, size, reinterpret_cast<jfloat *>(copy));
    clazzInfo.pQueuePriorities = copy;
    env->DeleteLocalRef(pQueuePrioritiesArray);
}

uint32_t
VkDeviceQueueCreateInfoAccessor::getqueueCount() {
    return (uint32_t) (jint) env->GetIntField(obj, queueCountField); // primitive
}

void
VkDeviceQueueCreateInfoAccessor::fromObject(VkDeviceQueueCreateInfo &clazzInfo) {
    clazzInfo.sType = getsType(); // Enum VkStructureType
    getpNext(clazzInfo); // Other void*
    clazzInfo.flags = getflags(); // Primitive uint32_t
    clazzInfo.queueFamilyIndex = getqueueFamilyIndex(); // Primitive uint32_t
    clazzInfo.queueCount = getqueueCount(); // Primitive uint32_t
    getpQueuePriorities(clazzInfo);  // float Object Array
}

void
VkDeviceQueueCreateInfoAccessor::getpNext(VkDeviceQueueCreateInfo &clazzInfo) {
    auto ref = (void *) (jobject) env->GetObjectField(obj, pNextField); // Any Object
    clazzInfo.pNext = ref;
}

VkDeviceQueueCreateInfoAccessor::~VkDeviceQueueCreateInfoAccessor() {
    env->DeleteLocalRef(clazz);
}


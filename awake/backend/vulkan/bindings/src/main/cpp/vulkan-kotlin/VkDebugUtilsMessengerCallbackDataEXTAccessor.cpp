/*
 *  VkDebugUtilsMessengerCallbackDataEXTAccessor.cpp
 *  Vulkan accessor for VkDebugUtilsMessengerCallbackDataEXT
 *  Created by Ron June Valdoz */

#include <includes/VkDebugUtilsMessengerCallbackDataEXTAccessor.h>
#include <includes/VulkanAccessArena.h>

VkDebugUtilsMessengerCallbackDataEXTAccessor::VkDebugUtilsMessengerCallbackDataEXTAccessor(
        JNIEnv *env, jobject obj) : env(env), obj(obj) {
    clazz = env->GetObjectClass(obj);
    sTypeField = env->GetFieldID(clazz, "sType",
                                 "Lcom/awakekt/awake/vulkan/enums/VkStructureType;");
    pNextField = env->GetFieldID(clazz, "pNext", "Ljava/lang/Object;");
    flagsField = env->GetFieldID(clazz, "flags", "I");
    pMessageIdNameField = env->GetFieldID(clazz, "pMessageIdName", "Ljava/lang/String;");
    messageIdNumberField = env->GetFieldID(clazz, "messageIdNumber", "I");
    pMessageField = env->GetFieldID(clazz, "pMessage", "Ljava/lang/String;");
    pQueueLabelsField = env->GetFieldID(clazz, "pQueueLabels",
                                        "[Lcom/awakekt/awake/vulkan/models/info/debug/VkDebugUtilsLabelEXT;");
    pCmdBufLabelsField = env->GetFieldID(clazz, "pCmdBufLabels",
                                         "[Lcom/awakekt/awake/vulkan/models/info/debug/VkDebugUtilsLabelEXT;");
    pObjectsField = env->GetFieldID(clazz, "pObjects",
                                    "[Lcom/awakekt/awake/vulkan/models/info/debug/VkDebugUtilsObjectNameInfoEXT;");
}

VkStructureType
VkDebugUtilsMessengerCallbackDataEXTAccessor::getsType() {
    auto sTypeEnum = (jobject) env->GetObjectField(obj, sTypeField);
    auto enumValue = (VkStructureType) enum_utils::getEnumFromObject(env, sTypeEnum);
    env->DeleteLocalRef(sTypeEnum); // release enum reference
    return enumValue;
}

uint32_t
VkDebugUtilsMessengerCallbackDataEXTAccessor::getflags() {
    return (uint32_t) (jint) env->GetIntField(obj, flagsField); // primitive
}

void
VkDebugUtilsMessengerCallbackDataEXTAccessor::getpNext(
        VkDebugUtilsMessengerCallbackDataEXT &clazzInfo) {
    auto ref = (void *) (jobject) env->GetObjectField(obj, pNextField); // Any Object
    clazzInfo.pNext = ref;
}

void
VkDebugUtilsMessengerCallbackDataEXTAccessor::getpMessage(
        VkDebugUtilsMessengerCallbackDataEXT &clazzInfo) {
    auto pMessageObj = (jstring) env->GetObjectField(obj, pMessageField);
    clazzInfo.pMessage = awake::VulkanAccessArena::current().copyJString(env, pMessageObj);
    if (pMessageObj != nullptr) {
        env->DeleteLocalRef(pMessageObj);
    }
}

void
VkDebugUtilsMessengerCallbackDataEXTAccessor::getpQueueLabels(
        VkDebugUtilsMessengerCallbackDataEXT &clazzInfo) {
    auto pQueueLabelsArray = (jobjectArray) env->GetObjectField(obj, pQueueLabelsField);
    if (pQueueLabelsArray == nullptr) {
        clazzInfo.queueLabelCount = 0;
        clazzInfo.pQueueLabels = nullptr;
        return;
    }
    auto size = env->GetArrayLength(pQueueLabelsArray);
    if (size <= 0) {
        clazzInfo.queueLabelCount = 0;
        clazzInfo.pQueueLabels = nullptr;
        env->DeleteLocalRef(pQueueLabelsArray);
        return;
    }
    auto count = static_cast<std::size_t>(size);
    auto *copy = awake::VulkanAccessArena::current().allocArray<VkDebugUtilsLabelEXT>(count);
    for (int i = 0; i < size; ++i) {
        auto element = (jobject) env->GetObjectArrayElement(pQueueLabelsArray, i);
        VkDebugUtilsLabelEXTAccessor accessor(env, element);
        accessor.fromObject(copy[i]);
        env->DeleteLocalRef(element);
    }
    clazzInfo.queueLabelCount = static_cast<uint32_t>(count);
    clazzInfo.pQueueLabels = copy;
    env->DeleteLocalRef(pQueueLabelsArray);
}

uint32_t
VkDebugUtilsMessengerCallbackDataEXTAccessor::getmessageIdNumber() {
    return (uint32_t) (jint) env->GetIntField(obj, messageIdNumberField); // primitive
}

void
VkDebugUtilsMessengerCallbackDataEXTAccessor::getpCmdBufLabels(
        VkDebugUtilsMessengerCallbackDataEXT &clazzInfo) {
    auto pCmdBufLabelsArray = (jobjectArray) env->GetObjectField(obj, pCmdBufLabelsField);
    if (pCmdBufLabelsArray == nullptr) {
        clazzInfo.cmdBufLabelCount = 0;
        clazzInfo.pCmdBufLabels = nullptr;
        return;
    }
    auto size = env->GetArrayLength(pCmdBufLabelsArray);
    if (size <= 0) {
        clazzInfo.cmdBufLabelCount = 0;
        clazzInfo.pCmdBufLabels = nullptr;
        env->DeleteLocalRef(pCmdBufLabelsArray);
        return;
    }
    auto count = static_cast<std::size_t>(size);
    auto *copy = awake::VulkanAccessArena::current().allocArray<VkDebugUtilsLabelEXT>(count);
    for (int i = 0; i < size; ++i) {
        auto element = (jobject) env->GetObjectArrayElement(pCmdBufLabelsArray, i);
        VkDebugUtilsLabelEXTAccessor accessor(env, element);
        accessor.fromObject(copy[i]);
        env->DeleteLocalRef(element);
    }
    clazzInfo.cmdBufLabelCount = static_cast<uint32_t>(count);
    clazzInfo.pCmdBufLabels = copy;
    env->DeleteLocalRef(pCmdBufLabelsArray);
}

void
VkDebugUtilsMessengerCallbackDataEXTAccessor::getpMessageIdName(
        VkDebugUtilsMessengerCallbackDataEXT &clazzInfo) {
    auto pMessageIdNameObj = (jstring) env->GetObjectField(obj, pMessageIdNameField);
    clazzInfo.pMessageIdName = awake::VulkanAccessArena::current().copyJString(env, pMessageIdNameObj);
    if (pMessageIdNameObj != nullptr) {
        env->DeleteLocalRef(pMessageIdNameObj);
    }
}

void
VkDebugUtilsMessengerCallbackDataEXTAccessor::fromObject(
        VkDebugUtilsMessengerCallbackDataEXT &clazzInfo) {
    clazzInfo.sType = getsType(); // Enum VkStructureType
    getpNext(clazzInfo); // Other void*
    clazzInfo.flags = getflags(); // Primitive uint32_t
    getpMessageIdName(clazzInfo); // Other const char*
    clazzInfo.messageIdNumber = getmessageIdNumber(); // Primitive uint32_t
    getpMessage(clazzInfo); // Other const char*
    getpQueueLabels(clazzInfo);  // VkDebugUtilsLabelEXT Object Array
    getpCmdBufLabels(clazzInfo);  // VkDebugUtilsLabelEXT Object Array
    getpObjects(clazzInfo);  // VkDebugUtilsObjectNameInfoEXT Object Array
}

void
VkDebugUtilsMessengerCallbackDataEXTAccessor::getpObjects(
        VkDebugUtilsMessengerCallbackDataEXT &clazzInfo) {
    auto pObjectsArray = (jobjectArray) env->GetObjectField(obj, pObjectsField);
    if (pObjectsArray == nullptr) {
        clazzInfo.objectCount = 0;
        clazzInfo.pObjects = nullptr;
        return;
    }
    auto size = env->GetArrayLength(pObjectsArray);
    if (size <= 0) {
        clazzInfo.objectCount = 0;
        clazzInfo.pObjects = nullptr;
        env->DeleteLocalRef(pObjectsArray);
        return;
    }
    auto count = static_cast<std::size_t>(size);
    auto *copy = awake::VulkanAccessArena::current().allocArray<VkDebugUtilsObjectNameInfoEXT>(count);
    for (int i = 0; i < size; ++i) {
        auto element = (jobject) env->GetObjectArrayElement(pObjectsArray, i);
        VkDebugUtilsObjectNameInfoEXTAccessor accessor(env, element);
        accessor.fromObject(copy[i]);
        env->DeleteLocalRef(element);
    }
    clazzInfo.objectCount = static_cast<uint32_t>(count);
    clazzInfo.pObjects = copy;
    env->DeleteLocalRef(pObjectsArray);
}

VkDebugUtilsMessengerCallbackDataEXTAccessor::~VkDebugUtilsMessengerCallbackDataEXTAccessor() {
    env->DeleteLocalRef(clazz);
}


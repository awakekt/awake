/*
 *  VkInstanceCreateInfoAccessor.cpp
 *  Vulkan accessor for VkInstanceCreateInfo
 *  Created by Ron June Valdoz */

#include <includes/VkInstanceCreateInfoAccessor.h>
#include <includes/VulkanAccessArena.h>

VkInstanceCreateInfoAccessor::VkInstanceCreateInfoAccessor(JNIEnv *env, jobject obj) : env(env),
                                                                                       obj(obj) {
    clazz = env->GetObjectClass(obj);
    sTypeField = env->GetFieldID(clazz, "sType",
                                 "Lcom/awakekt/awake/vulkan/enums/VkStructureType;");
    pNextField = env->GetFieldID(clazz, "pNext", "Ljava/lang/Object;");
    flagsField = env->GetFieldID(clazz, "flags", "I");
    pApplicationInfoField = env->GetFieldID(clazz, "pApplicationInfo",
                                            "[Lcom/awakekt/awake/vulkan/models/info/VkApplicationInfo;");
    ppEnabledLayerNamesField = env->GetFieldID(clazz, "ppEnabledLayerNames", "[Ljava/lang/String;");
    ppEnabledExtensionNamesField = env->GetFieldID(clazz, "ppEnabledExtensionNames",
                                                   "[Ljava/lang/String;");
}

VkStructureType
VkInstanceCreateInfoAccessor::getsType() {
    auto sTypeEnum = (jobject) env->GetObjectField(obj, sTypeField);
    auto enumValue = (VkStructureType) enum_utils::getEnumFromObject(env, sTypeEnum);
    env->DeleteLocalRef(sTypeEnum); // release enum reference
    return enumValue;
}

uint32_t
VkInstanceCreateInfoAccessor::getflags() {
    return (uint32_t) (jint) env->GetIntField(obj, flagsField); // primitive
}

void
VkInstanceCreateInfoAccessor::getppEnabledExtensionNames(VkInstanceCreateInfo &clazzInfo) {
    auto ppEnabledExtensionNamesArray = (jobjectArray) env->GetObjectField(obj,
                                                                           ppEnabledExtensionNamesField);
    if (ppEnabledExtensionNamesArray == nullptr) {
        clazzInfo.enabledExtensionCount = 0;
        clazzInfo.ppEnabledExtensionNames = nullptr;
        return;
    }
    auto size = env->GetArrayLength(ppEnabledExtensionNamesArray);
    if (size <= 0) {
        clazzInfo.enabledExtensionCount = 0;
        clazzInfo.ppEnabledExtensionNames = nullptr;
        env->DeleteLocalRef(ppEnabledExtensionNamesArray);
        return;
    }
    auto count = static_cast<std::size_t>(size);
    auto &arena = awake::VulkanAccessArena::current();
    auto *copy = arena.allocArray<const char *>(count);
    for (int i = 0; i < size; ++i) {
        auto element = (jstring) env->GetObjectArrayElement(ppEnabledExtensionNamesArray, i);
        copy[i] = arena.copyJString(env, element);
        if (element != nullptr) {
            env->DeleteLocalRef(element);
        }
    }
    clazzInfo.enabledExtensionCount = static_cast<uint32_t>(count);
    clazzInfo.ppEnabledExtensionNames = copy;
    env->DeleteLocalRef(ppEnabledExtensionNamesArray);
}

void
VkInstanceCreateInfoAccessor::getpNext(VkInstanceCreateInfo &clazzInfo) {
    auto ref = (void *) (jobject) env->GetObjectField(obj, pNextField); // Any Object
    clazzInfo.pNext = ref;
}

void
VkInstanceCreateInfoAccessor::fromObject(VkInstanceCreateInfo &clazzInfo) {
    clazzInfo.sType = getsType(); // Enum VkStructureType
    getpNext(clazzInfo); // Other void*
    clazzInfo.flags = getflags(); // Primitive uint32_t
    getpApplicationInfo(clazzInfo);  // VkApplicationInfo Object Array
    getppEnabledLayerNames(clazzInfo);  // String Object Array
    getppEnabledExtensionNames(clazzInfo);  // String Object Array
}

void
VkInstanceCreateInfoAccessor::getpApplicationInfo(VkInstanceCreateInfo &clazzInfo) {
    auto pApplicationInfoArray = (jobjectArray) env->GetObjectField(obj, pApplicationInfoField);
    if (pApplicationInfoArray == nullptr) {
        clazzInfo.pApplicationInfo = nullptr;
        return;
    }
    auto size = env->GetArrayLength(pApplicationInfoArray);
    if (size <= 0) {
        clazzInfo.pApplicationInfo = nullptr;
        env->DeleteLocalRef(pApplicationInfoArray);
        return;
    }
    auto count = static_cast<std::size_t>(size);
    auto *copy = awake::VulkanAccessArena::current().allocArray<VkApplicationInfo>(count);
    for (int i = 0; i < size; ++i) {
        auto element = (jobject) env->GetObjectArrayElement(pApplicationInfoArray, i);
        VkApplicationInfoAccessor accessor(env, element);
        accessor.fromObject(copy[i]);
        env->DeleteLocalRef(element);
    }
    clazzInfo.pApplicationInfo = copy;
    env->DeleteLocalRef(pApplicationInfoArray);
}

void
VkInstanceCreateInfoAccessor::getppEnabledLayerNames(VkInstanceCreateInfo &clazzInfo) {
    auto ppEnabledLayerNamesArray = (jobjectArray) env->GetObjectField(obj,
                                                                       ppEnabledLayerNamesField);
    if (ppEnabledLayerNamesArray == nullptr) {
        clazzInfo.enabledLayerCount = 0;
        clazzInfo.ppEnabledLayerNames = nullptr;
        return;
    }
    auto size = env->GetArrayLength(ppEnabledLayerNamesArray);
    if (size <= 0) {
        clazzInfo.enabledLayerCount = 0;
        clazzInfo.ppEnabledLayerNames = nullptr;
        env->DeleteLocalRef(ppEnabledLayerNamesArray);
        return;
    }
    auto count = static_cast<std::size_t>(size);
    auto &arena = awake::VulkanAccessArena::current();
    auto *copy = arena.allocArray<const char *>(count);
    for (int i = 0; i < size; ++i) {
        auto element = (jstring) env->GetObjectArrayElement(ppEnabledLayerNamesArray, i);
        copy[i] = arena.copyJString(env, element);
        if (element != nullptr) {
            env->DeleteLocalRef(element);
        }
    }
    clazzInfo.enabledLayerCount = static_cast<uint32_t>(count);
    clazzInfo.ppEnabledLayerNames = copy;
    env->DeleteLocalRef(ppEnabledLayerNamesArray);
}

VkInstanceCreateInfoAccessor::~VkInstanceCreateInfoAccessor() {
    env->DeleteLocalRef(clazz);
}


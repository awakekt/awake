/*
 *  VkRenderPassCreateInfoAccessor.cpp
 *  Vulkan accessor for VkRenderPassCreateInfo
 *  Created by Ron June Valdoz */

#include <includes/VkRenderPassCreateInfoAccessor.h>
#include <includes/VulkanAccessArena.h>

VkRenderPassCreateInfoAccessor::VkRenderPassCreateInfoAccessor(JNIEnv *env, jobject obj) : env(env),
                                                                                           obj(obj) {
    clazz = env->GetObjectClass(obj);
    sTypeField = env->GetFieldID(clazz, "sType",
                                 "Lcom/awakekt/awake/vulkan/enums/VkStructureType;");
    pNextField = env->GetFieldID(clazz, "pNext", "Ljava/lang/Object;");
    flagsField = env->GetFieldID(clazz, "flags", "I");
    pAttachmentsField = env->GetFieldID(clazz, "pAttachments",
                                        "[Lcom/awakekt/awake/vulkan/models/VkAttachmentDescription;");
    pSubpassesField = env->GetFieldID(clazz, "pSubpasses",
                                      "[Lcom/awakekt/awake/vulkan/models/info/VkSubpassDescription;");
    pDependenciesField = env->GetFieldID(clazz, "pDependencies",
                                         "[Lcom/awakekt/awake/vulkan/models/VkSubpassDependency;");
}

VkStructureType
VkRenderPassCreateInfoAccessor::getsType() {
    auto sTypeEnum = (jobject) env->GetObjectField(obj, sTypeField);
    auto enumValue = (VkStructureType) enum_utils::getEnumFromObject(env, sTypeEnum);
    env->DeleteLocalRef(sTypeEnum); // release enum reference
    return enumValue;
}

uint32_t
VkRenderPassCreateInfoAccessor::getflags() {
    return (uint32_t) (jint) env->GetIntField(obj, flagsField); // primitive
}

void
VkRenderPassCreateInfoAccessor::getpNext(VkRenderPassCreateInfo &clazzInfo) {
    auto ref = (void *) (jobject) env->GetObjectField(obj, pNextField); // Any Object
    clazzInfo.pNext = ref;
}

void
VkRenderPassCreateInfoAccessor::getpAttachments(VkRenderPassCreateInfo &clazzInfo) {
    auto pAttachmentsArray = (jobjectArray) env->GetObjectField(obj, pAttachmentsField);
    if (pAttachmentsArray == nullptr) {
        clazzInfo.attachmentCount = 0;
        clazzInfo.pAttachments = nullptr;
        return;
    }
    auto size = env->GetArrayLength(pAttachmentsArray);
    if (size <= 0) {
        clazzInfo.attachmentCount = 0;
        clazzInfo.pAttachments = nullptr;
        env->DeleteLocalRef(pAttachmentsArray);
        return;
    }
    auto count = static_cast<std::size_t>(size);
    auto *copy = awake::VulkanAccessArena::current().allocArray<VkAttachmentDescription>(count);
    for (int i = 0; i < size; ++i) {
        auto element = (jobject) env->GetObjectArrayElement(pAttachmentsArray, i);
        VkAttachmentDescriptionAccessor accessor(env, element);
        accessor.fromObject(copy[i]);
        env->DeleteLocalRef(element);
    }
    clazzInfo.attachmentCount = static_cast<uint32_t>(count);
    clazzInfo.pAttachments = copy;
    env->DeleteLocalRef(pAttachmentsArray);
}

void
VkRenderPassCreateInfoAccessor::getpDependencies(VkRenderPassCreateInfo &clazzInfo) {
    auto pDependenciesArray = (jobjectArray) env->GetObjectField(obj, pDependenciesField);
    if (pDependenciesArray == nullptr) {
        clazzInfo.dependencyCount = 0;
        clazzInfo.pDependencies = nullptr;
        return;
    }
    auto size = env->GetArrayLength(pDependenciesArray);
    if (size <= 0) {
        clazzInfo.dependencyCount = 0;
        clazzInfo.pDependencies = nullptr;
        env->DeleteLocalRef(pDependenciesArray);
        return;
    }
    auto count = static_cast<std::size_t>(size);
    auto *copy = awake::VulkanAccessArena::current().allocArray<VkSubpassDependency>(count);
    for (int i = 0; i < size; ++i) {
        auto element = (jobject) env->GetObjectArrayElement(pDependenciesArray, i);
        VkSubpassDependencyAccessor accessor(env, element);
        accessor.fromObject(copy[i]);
        env->DeleteLocalRef(element);
    }
    clazzInfo.dependencyCount = static_cast<uint32_t>(count);
    clazzInfo.pDependencies = copy;
    env->DeleteLocalRef(pDependenciesArray);
}

void
VkRenderPassCreateInfoAccessor::fromObject(VkRenderPassCreateInfo &clazzInfo) {
    clazzInfo.sType = getsType(); // Enum VkStructureType
    getpNext(clazzInfo); // Other void*
    clazzInfo.flags = getflags(); // Primitive uint32_t
    getpAttachments(clazzInfo);  // VkAttachmentDescription Object Array
    getpSubpasses(clazzInfo);  // VkSubpassDescription Object Array
    getpDependencies(clazzInfo);  // VkSubpassDependency Object Array
}

void
VkRenderPassCreateInfoAccessor::getpSubpasses(VkRenderPassCreateInfo &clazzInfo) {
    auto pSubpassesArray = (jobjectArray) env->GetObjectField(obj, pSubpassesField);
    if (pSubpassesArray == nullptr) {
        clazzInfo.subpassCount = 0;
        clazzInfo.pSubpasses = nullptr;
        return;
    }
    auto size = env->GetArrayLength(pSubpassesArray);
    if (size <= 0) {
        clazzInfo.subpassCount = 0;
        clazzInfo.pSubpasses = nullptr;
        env->DeleteLocalRef(pSubpassesArray);
        return;
    }
    auto count = static_cast<std::size_t>(size);
    auto *copy = awake::VulkanAccessArena::current().allocArray<VkSubpassDescription>(count);
    for (int i = 0; i < size; ++i) {
        auto element = (jobject) env->GetObjectArrayElement(pSubpassesArray, i);
        VkSubpassDescriptionAccessor accessor(env, element);
        accessor.fromObject(copy[i]);
        env->DeleteLocalRef(element);
    }
    clazzInfo.subpassCount = static_cast<uint32_t>(count);
    clazzInfo.pSubpasses = copy;
    env->DeleteLocalRef(pSubpassesArray);
}

VkRenderPassCreateInfoAccessor::~VkRenderPassCreateInfoAccessor() {
    env->DeleteLocalRef(clazz);
}


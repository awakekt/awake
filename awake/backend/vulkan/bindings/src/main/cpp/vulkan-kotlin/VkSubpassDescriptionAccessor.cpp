/*
 *  VkSubpassDescriptionAccessor.cpp
 *  Vulkan accessor for VkSubpassDescription
 *  Created by Ron June Valdoz */

#include <includes/VkSubpassDescriptionAccessor.h>
#include <includes/VulkanAccessArena.h>

VkSubpassDescriptionAccessor::VkSubpassDescriptionAccessor(JNIEnv *env, jobject obj) : env(env),
                                                                                       obj(obj) {
    clazz = env->GetObjectClass(obj);
    flagsField = env->GetFieldID(clazz, "flags", "I");
    pipelineBindPointField = env->GetFieldID(clazz, "pipelineBindPoint",
                                             "Lcom/awakekt/awake/vulkan/enums/VkPipelineBindPoint;");
    pInputAttachmentsField = env->GetFieldID(clazz, "pInputAttachments",
                                             "[Lcom/awakekt/awake/vulkan/models/VkAttachmentReference;");
    pColorAttachmentsField = env->GetFieldID(clazz, "pColorAttachments",
                                             "[Lcom/awakekt/awake/vulkan/models/VkAttachmentReference;");
    pResolveAttachmentsField = env->GetFieldID(clazz, "pResolveAttachments",
                                               "[Lcom/awakekt/awake/vulkan/models/VkAttachmentReference;");
    pDepthStencilAttachmentField = env->GetFieldID(clazz, "pDepthStencilAttachment",
                                                   "[Lcom/awakekt/awake/vulkan/models/VkAttachmentReference;");
    pPreserveAttachmentsField = env->GetFieldID(clazz, "pPreserveAttachments", "[I");
}

uint32_t
VkSubpassDescriptionAccessor::getflags() {
    return (uint32_t) (jint) env->GetIntField(obj, flagsField); // primitive
}

void
VkSubpassDescriptionAccessor::getpInputAttachments(VkSubpassDescription &clazzInfo) {
    auto pInputAttachmentsArray = (jobjectArray) env->GetObjectField(obj, pInputAttachmentsField);
    if (pInputAttachmentsArray == nullptr) {
        clazzInfo.inputAttachmentCount = 0;
        clazzInfo.pInputAttachments = nullptr;
        return;
    }
    auto size = env->GetArrayLength(pInputAttachmentsArray);
    if (size <= 0) {
        clazzInfo.inputAttachmentCount = 0;
        clazzInfo.pInputAttachments = nullptr;
        env->DeleteLocalRef(pInputAttachmentsArray);
        return;
    }
    auto count = static_cast<std::size_t>(size);
    auto *copy = awake::VulkanAccessArena::current().allocArray<VkAttachmentReference>(count);
    for (int i = 0; i < size; ++i) {
        auto element = (jobject) env->GetObjectArrayElement(pInputAttachmentsArray, i);
        VkAttachmentReferenceAccessor accessor(env, element);
        accessor.fromObject(copy[i]);
        env->DeleteLocalRef(element);
    }
    clazzInfo.inputAttachmentCount = static_cast<uint32_t>(count);
    clazzInfo.pInputAttachments = copy;
    env->DeleteLocalRef(pInputAttachmentsArray);
}

void
VkSubpassDescriptionAccessor::getpColorAttachments(VkSubpassDescription &clazzInfo) {
    auto pColorAttachmentsArray = (jobjectArray) env->GetObjectField(obj, pColorAttachmentsField);
    if (pColorAttachmentsArray == nullptr) {
        clazzInfo.colorAttachmentCount = 0;
        clazzInfo.pColorAttachments = nullptr;
        return;
    }
    auto size = env->GetArrayLength(pColorAttachmentsArray);
    if (size <= 0) {
        clazzInfo.colorAttachmentCount = 0;
        clazzInfo.pColorAttachments = nullptr;
        env->DeleteLocalRef(pColorAttachmentsArray);
        return;
    }
    auto count = static_cast<std::size_t>(size);
    auto *copy = awake::VulkanAccessArena::current().allocArray<VkAttachmentReference>(count);
    for (int i = 0; i < size; ++i) {
        auto element = (jobject) env->GetObjectArrayElement(pColorAttachmentsArray, i);
        VkAttachmentReferenceAccessor accessor(env, element);
        accessor.fromObject(copy[i]);
        env->DeleteLocalRef(element);
    }
    clazzInfo.colorAttachmentCount = static_cast<uint32_t>(count);
    clazzInfo.pColorAttachments = copy;
    env->DeleteLocalRef(pColorAttachmentsArray);
}

VkPipelineBindPoint
VkSubpassDescriptionAccessor::getpipelineBindPoint() {
    auto pipelineBindPointEnum = (jobject) env->GetObjectField(obj, pipelineBindPointField);
    auto enumValue = (VkPipelineBindPoint) enum_utils::getEnumFromObject(env,
                                                                         pipelineBindPointEnum);
    env->DeleteLocalRef(pipelineBindPointEnum); // release enum reference
    return enumValue;
}

void
VkSubpassDescriptionAccessor::getpResolveAttachments(VkSubpassDescription &clazzInfo) {
    auto pResolveAttachmentsArray = (jobjectArray) env->GetObjectField(obj,
                                                                       pResolveAttachmentsField);
    if (pResolveAttachmentsArray == nullptr) {
        clazzInfo.pResolveAttachments = nullptr;
        return;
    }
    auto size = env->GetArrayLength(pResolveAttachmentsArray);
    if (size <= 0) {
        clazzInfo.pResolveAttachments = nullptr;
        env->DeleteLocalRef(pResolveAttachmentsArray);
        return;
    }
    auto count = static_cast<std::size_t>(size);
    auto *copy = awake::VulkanAccessArena::current().allocArray<VkAttachmentReference>(count);
    for (int i = 0; i < size; ++i) {
        auto element = (jobject) env->GetObjectArrayElement(pResolveAttachmentsArray, i);
        VkAttachmentReferenceAccessor accessor(env, element);
        accessor.fromObject(copy[i]);
        env->DeleteLocalRef(element);
    }
    clazzInfo.pResolveAttachments = copy;
    env->DeleteLocalRef(pResolveAttachmentsArray);
}

void
VkSubpassDescriptionAccessor::fromObject(VkSubpassDescription &clazzInfo) {
    clazzInfo.flags = getflags(); // Primitive uint32_t
    clazzInfo.pipelineBindPoint = getpipelineBindPoint(); // Enum VkPipelineBindPoint
    getpInputAttachments(clazzInfo);  // VkAttachmentReference Object Array
    getpColorAttachments(clazzInfo);  // VkAttachmentReference Object Array
    getpResolveAttachments(clazzInfo);  // VkAttachmentReference Object Array
    getpDepthStencilAttachment(clazzInfo);  // VkAttachmentReference Object Array
    getpPreserveAttachments(clazzInfo);  // int Object Array
}

void
VkSubpassDescriptionAccessor::getpPreserveAttachments(VkSubpassDescription &clazzInfo) {
    auto pPreserveAttachmentsArray = (jintArray) env->GetObjectField(obj,
                                                                     pPreserveAttachmentsField);
    if (pPreserveAttachmentsArray == nullptr) {
        clazzInfo.preserveAttachmentCount = 0;
        clazzInfo.pPreserveAttachments = nullptr;
        return;
    }
    auto size = env->GetArrayLength(pPreserveAttachmentsArray);
    if (size <= 0) {
        clazzInfo.preserveAttachmentCount = 0;
        clazzInfo.pPreserveAttachments = nullptr;
        env->DeleteLocalRef(pPreserveAttachmentsArray);
        return;
    }
    auto count = static_cast<std::size_t>(size);
    auto *copy = awake::VulkanAccessArena::current().allocArray<uint32_t>(count);
    env->GetIntArrayRegion(pPreserveAttachmentsArray, 0, size, reinterpret_cast<jint *>(copy));
    clazzInfo.preserveAttachmentCount = static_cast<uint32_t>(count);
    clazzInfo.pPreserveAttachments = copy;
    env->DeleteLocalRef(pPreserveAttachmentsArray);
}

void
VkSubpassDescriptionAccessor::getpDepthStencilAttachment(VkSubpassDescription &clazzInfo) {
    auto pDepthStencilAttachmentArray = (jobjectArray) env->GetObjectField(obj,
                                                                           pDepthStencilAttachmentField);
    if (pDepthStencilAttachmentArray == nullptr) {
        clazzInfo.pDepthStencilAttachment = nullptr;
        return;
    }
    auto size = env->GetArrayLength(pDepthStencilAttachmentArray);
    if (size <= 0) {
        clazzInfo.pDepthStencilAttachment = nullptr;
        env->DeleteLocalRef(pDepthStencilAttachmentArray);
        return;
    }
    auto count = static_cast<std::size_t>(size);
    auto *copy = awake::VulkanAccessArena::current().allocArray<VkAttachmentReference>(count);
    for (int i = 0; i < size; ++i) {
        auto element = (jobject) env->GetObjectArrayElement(pDepthStencilAttachmentArray, i);
        VkAttachmentReferenceAccessor accessor(env, element);
        accessor.fromObject(copy[i]);
        env->DeleteLocalRef(element);
    }
    clazzInfo.pDepthStencilAttachment = copy;
    env->DeleteLocalRef(pDepthStencilAttachmentArray);
}

VkSubpassDescriptionAccessor::~VkSubpassDescriptionAccessor() {
    env->DeleteLocalRef(clazz);
}


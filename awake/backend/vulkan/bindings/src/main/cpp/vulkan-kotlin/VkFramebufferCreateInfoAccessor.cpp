/*
 *  VkFramebufferCreateInfoAccessor.cpp
 *  Vulkan accessor for VkFramebufferCreateInfo
 *  Created by Ron June Valdoz */

#include <includes/VkFramebufferCreateInfoAccessor.h>
#include <includes/VulkanAccessArena.h>

VkFramebufferCreateInfoAccessor::VkFramebufferCreateInfoAccessor(JNIEnv *env, jobject obj) : env(
        env), obj(obj) {
    clazz = env->GetObjectClass(obj);
    sTypeField = env->GetFieldID(clazz, "sType",
                                 "Lcom/awakekt/awake/vulkan/enums/VkStructureType;");
    pNextField = env->GetFieldID(clazz, "pNext", "Ljava/lang/Object;");
    flagsField = env->GetFieldID(clazz, "flags", "I");
    renderPassField = env->GetFieldID(clazz, "renderPass", "J");
    pAttachmentsField = env->GetFieldID(clazz, "pAttachments", "[Ljava/lang/Long;");
    widthField = env->GetFieldID(clazz, "width", "I");
    heightField = env->GetFieldID(clazz, "height", "I");
    layersField = env->GetFieldID(clazz, "layers", "I");
}

VkStructureType
VkFramebufferCreateInfoAccessor::getsType() {
    auto sTypeEnum = (jobject) env->GetObjectField(obj, sTypeField);
    auto enumValue = (VkStructureType) enum_utils::getEnumFromObject(env, sTypeEnum);
    env->DeleteLocalRef(sTypeEnum); // release enum reference
    return enumValue;
}

uint32_t
VkFramebufferCreateInfoAccessor::getwidth() {
    return (uint32_t) (jint) env->GetIntField(obj, widthField); // primitive
}

void
VkFramebufferCreateInfoAccessor::getpNext(VkFramebufferCreateInfo &clazzInfo) {
    auto ref = (void *) (jobject) env->GetObjectField(obj, pNextField); // Any Object
    clazzInfo.pNext = ref;
}

uint32_t
VkFramebufferCreateInfoAccessor::getflags() {
    return (uint32_t) (jint) env->GetIntField(obj, flagsField); // primitive
}

void
VkFramebufferCreateInfoAccessor::getpAttachments(VkFramebufferCreateInfo &clazzInfo) {
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
    auto *copy = awake::VulkanAccessArena::current().allocArray<VkImageView>(count);
    for (int i = 0; i < size; ++i) {
        auto element = env->GetObjectArrayElement(pAttachmentsArray, i);
        jclass elementClass = env->GetObjectClass(element);
        jmethodID getValueMethod = env->GetMethodID(elementClass, "longValue", "()J");
        jlong value = env->CallLongMethod(element, getValueMethod);
        copy[i] = reinterpret_cast<VkImageView>(value);
        env->DeleteLocalRef(elementClass);
        env->DeleteLocalRef(element);
    }
    clazzInfo.attachmentCount = static_cast<uint32_t>(count);
    clazzInfo.pAttachments = copy;
    env->DeleteLocalRef(pAttachmentsArray);
}

void
VkFramebufferCreateInfoAccessor::fromObject(VkFramebufferCreateInfo &clazzInfo) {
    clazzInfo.sType = getsType(); // Enum VkStructureType
    getpNext(clazzInfo); // Other void*
    clazzInfo.flags = getflags(); // Primitive uint32_t
    clazzInfo.renderPass = getrenderPass(); // VkHandle
    getpAttachments(clazzInfo);  // Long Object Array
    clazzInfo.width = getwidth(); // Primitive uint32_t
    clazzInfo.height = getheight(); // Primitive uint32_t
    clazzInfo.layers = getlayers(); // Primitive uint32_t
}

uint32_t
VkFramebufferCreateInfoAccessor::getlayers() {
    return (uint32_t) (jint) env->GetIntField(obj, layersField); // primitive
}

VkRenderPass
VkFramebufferCreateInfoAccessor::getrenderPass() {
    auto value = env->GetLongField(obj, renderPassField);
    if (value == 0) {
        return VK_NULL_HANDLE;
    }
    return reinterpret_cast<VkRenderPass>(value); // VkHandle
}

uint32_t
VkFramebufferCreateInfoAccessor::getheight() {
    return (uint32_t) (jint) env->GetIntField(obj, heightField); // primitive
}

VkFramebufferCreateInfoAccessor::~VkFramebufferCreateInfoAccessor() {
    env->DeleteLocalRef(clazz);
}


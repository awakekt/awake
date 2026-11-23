/*
 *  VkRenderPassBeginInfoAccessor.cpp
 *  Vulkan accessor for VkRenderPassBeginInfo
 *  Created by Ron June Valdoz */

#include <includes/VkRenderPassBeginInfoAccessor.h>

VkRenderPassBeginInfoAccessor::VkRenderPassBeginInfoAccessor(JNIEnv *env, jobject obj) : env(env),
                                                                                         obj(obj) {
    clazz = env->GetObjectClass(obj);
    sTypeField = env->GetFieldID(clazz, "sType",
                                 "Lcom/awakekt/awake/vulkan/enums/VkStructureType;");
    pNextField = env->GetFieldID(clazz, "pNext", "Ljava/lang/Object;");
    renderPassField = env->GetFieldID(clazz, "renderPass", "J");
    framebufferField = env->GetFieldID(clazz, "framebuffer", "J");
    renderAreaField = env->GetFieldID(clazz, "renderArea",
                                      "Lcom/awakekt/awake/vulkan/models/VkRect2D;");
    pClearValuesField = env->GetFieldID(clazz, "pClearValues",
                                        "[Lcom/awakekt/awake/vulkan/models/VkClearValue;");
}

VkStructureType
VkRenderPassBeginInfoAccessor::getsType() {
    auto sTypeEnum = (jobject) env->GetObjectField(obj, sTypeField);
    auto enumValue = (VkStructureType) enum_utils::getEnumFromObject(env, sTypeEnum);
    env->DeleteLocalRef(sTypeEnum); // release enum reference
    return enumValue;
}

VkFramebuffer
VkRenderPassBeginInfoAccessor::getframebuffer() {
    auto value = env->GetLongField(obj, framebufferField);
    if (value == 0) {
        return VK_NULL_HANDLE;
    }
    return reinterpret_cast<VkFramebuffer>(value); // VkHandle
}

void
VkRenderPassBeginInfoAccessor::fromObject(VkRenderPassBeginInfo &clazzInfo) {
    clazzInfo.sType = getsType(); // Enum VkStructureType
    getpNext(clazzInfo); // Other void*
    clazzInfo.renderPass = getrenderPass(); // VkHandle
    clazzInfo.framebuffer = getframebuffer(); // VkHandle
    getrenderArea(clazzInfo); // Other VkRect2D
    getpClearValues(clazzInfo);  // VkClearValue Object Array
}

void
VkRenderPassBeginInfoAccessor::getpClearValues(VkRenderPassBeginInfo &clazzInfo) {
    clearValuesStorage.clear();
    auto pClearValuesArray = (jobjectArray) env->GetObjectField(obj, pClearValuesField);
    if (pClearValuesArray == nullptr) {
        clazzInfo.clearValueCount = 0;
        clazzInfo.pClearValues = nullptr;
        return;
    }
    auto size = env->GetArrayLength(pClearValuesArray);
    if (size > 0) {
        clearValuesStorage.reserve(static_cast<size_t>(size));
        auto vkClearColorValueClass = env->FindClass(
                "com/awakekt/awake/vulkan/models/VkClearColorValue");
        auto vkClearDepthStencilValueClass = env->FindClass(
                "com/awakekt/awake/vulkan/models/VkClearDepthStencilValue");
        for (int i = 0; i < size; ++i) {
            auto element = (jobject) env->GetObjectArrayElement(pClearValuesArray, i);
            if (element != nullptr) {
                if (env->IsInstanceOf(element, vkClearColorValueClass)) {
                    VkClearColorValueAccessor accessor(env, element);
                    VkClearColorValue ref{};
                    accessor.fromObject(ref);
                    VkClearValue value = {.color {ref}};
                    clearValuesStorage.push_back(value);
                } else if (env->IsInstanceOf(element, vkClearDepthStencilValueClass)) {
                    VkClearDepthStencilValueAccessor accessor(env, element);
                    VkClearDepthStencilValue ref{};
                    accessor.fromObject(ref);
                    VkClearValue value = {.depthStencil {ref}};
                    clearValuesStorage.push_back(value);
                }
                env->DeleteLocalRef(element);
            }
        }
        env->DeleteLocalRef(vkClearColorValueClass);
        env->DeleteLocalRef(vkClearDepthStencilValueClass);
    }
    clazzInfo.clearValueCount = static_cast<uint32_t>(clearValuesStorage.size());
    clazzInfo.pClearValues = clearValuesStorage.empty() ? nullptr : clearValuesStorage.data();
    env->DeleteLocalRef(pClearValuesArray);
}

VkRenderPass
VkRenderPassBeginInfoAccessor::getrenderPass() {
    auto value = env->GetLongField(obj, renderPassField);
    if (value == 0) {
        return VK_NULL_HANDLE;
    }
    return reinterpret_cast<VkRenderPass>(value); // VkHandle
}

void
VkRenderPassBeginInfoAccessor::getpNext(VkRenderPassBeginInfo &clazzInfo) {
    auto ref = (void *) (jobject) env->GetObjectField(obj, pNextField); // Any Object
    clazzInfo.pNext = ref;
}

void
VkRenderPassBeginInfoAccessor::getrenderArea(VkRenderPassBeginInfo &clazzInfo) {
    auto renderAreaObj = (jobject) env->GetObjectField(obj, renderAreaField);
    if (renderAreaObj == nullptr) {
        env->DeleteLocalRef(renderAreaObj); // Delete null object reference
        return;
    }
    VkRect2DAccessor accessor(env, renderAreaObj);
    VkRect2D ref{};
    accessor.fromObject(ref);
    clazzInfo.renderArea = ref;
    env->DeleteLocalRef(renderAreaObj); // Delete object reference
}

VkRenderPassBeginInfoAccessor::~VkRenderPassBeginInfoAccessor() {
    env->DeleteLocalRef(clazz);
}


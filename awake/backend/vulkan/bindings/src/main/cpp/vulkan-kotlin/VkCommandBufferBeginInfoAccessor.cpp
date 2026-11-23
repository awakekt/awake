/*
 *  VkCommandBufferBeginInfoAccessor.cpp
 *  Vulkan accessor for VkCommandBufferBeginInfo
 *  Created by Ron June Valdoz */

#include <includes/VkCommandBufferBeginInfoAccessor.h>

VkCommandBufferBeginInfoAccessor::VkCommandBufferBeginInfoAccessor(JNIEnv *env, jobject obj) : env(
        env), obj(obj) {
    clazz = env->GetObjectClass(obj);
    sTypeField = env->GetFieldID(clazz, "sType",
                                 "Lcom/awakekt/awake/vulkan/enums/VkStructureType;");
    pNextField = env->GetFieldID(clazz, "pNext", "Ljava/lang/Object;");
    flagsField = env->GetFieldID(clazz, "flags", "I");
    pInheritanceInfoField = env->GetFieldID(clazz, "pInheritanceInfo",
                                            "[Lcom/awakekt/awake/vulkan/models/info/VkCommandBufferInheritanceInfo;");
}

VkStructureType
VkCommandBufferBeginInfoAccessor::getsType() {
    auto sTypeEnum = (jobject) env->GetObjectField(obj, sTypeField);
    auto enumValue = (VkStructureType) enum_utils::getEnumFromObject(env, sTypeEnum);
    env->DeleteLocalRef(sTypeEnum); // release enum reference
    return enumValue;
}

uint32_t
VkCommandBufferBeginInfoAccessor::getflags() {
    return (uint32_t) (jint) env->GetIntField(obj, flagsField); // primitive
}

void
VkCommandBufferBeginInfoAccessor::getpInheritanceInfo(VkCommandBufferBeginInfo &clazzInfo) {
    inheritanceInfoStorage.clear();
    auto pInheritanceInfoArray = (jobjectArray) env->GetObjectField(obj, pInheritanceInfoField);
    if (pInheritanceInfoArray == nullptr) {
        clazzInfo.pInheritanceInfo = nullptr;
        return;
    }
    auto size = env->GetArrayLength(pInheritanceInfoArray);
    if (size > 0) {
        inheritanceInfoStorage.reserve(static_cast<size_t>(size));
        for (int i = 0; i < size; ++i) {
            auto element = (jobject) env->GetObjectArrayElement(pInheritanceInfoArray, i);
            VkCommandBufferInheritanceInfoAccessor accessor(env, element);
            VkCommandBufferInheritanceInfo ref{};
            accessor.fromObject(ref);
            inheritanceInfoStorage.push_back(ref);
            env->DeleteLocalRef(element);
        }
    }
    clazzInfo.pInheritanceInfo = inheritanceInfoStorage.empty() ? nullptr : inheritanceInfoStorage.data();
    env->DeleteLocalRef(pInheritanceInfoArray);
}

void
VkCommandBufferBeginInfoAccessor::getpNext(VkCommandBufferBeginInfo &clazzInfo) {
    auto ref = (void *) (jobject) env->GetObjectField(obj, pNextField); // Any Object
    clazzInfo.pNext = ref;
}

void
VkCommandBufferBeginInfoAccessor::fromObject(VkCommandBufferBeginInfo &clazzInfo) {
    clazzInfo.sType = getsType(); // Enum VkStructureType
    getpNext(clazzInfo); // Other void*
    clazzInfo.flags = getflags(); // Primitive uint32_t
    getpInheritanceInfo(clazzInfo);  // VkCommandBufferInheritanceInfo Object Array
}

VkCommandBufferBeginInfoAccessor::~VkCommandBufferBeginInfoAccessor() {
    env->DeleteLocalRef(clazz);
}


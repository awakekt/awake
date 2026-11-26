/*
 *  VkShaderModuleCreateInfoAccessor.cpp
 *  Vulkan accessor for VkShaderModuleCreateInfo
 *  Created by Ron June Valdoz */

#include <includes/VkShaderModuleCreateInfoAccessor.h>
#include <includes/VulkanAccessArena.h>

VkShaderModuleCreateInfoAccessor::VkShaderModuleCreateInfoAccessor(JNIEnv *env, jobject obj) : env(
        env), obj(obj) {
    clazz = env->GetObjectClass(obj);
    sTypeField = env->GetFieldID(clazz, "sType",
                                 "Lcom/awakekt/awake/vulkan/enums/VkStructureType;");
    pNextField = env->GetFieldID(clazz, "pNext", "Ljava/lang/Object;");
    flagsField = env->GetFieldID(clazz, "flags", "I");
    pCodeField = env->GetFieldID(clazz, "pCode", "[I");
}

VkStructureType
VkShaderModuleCreateInfoAccessor::getsType() {
    auto sTypeEnum = (jobject) env->GetObjectField(obj, sTypeField);
    auto enumValue = (VkStructureType) enum_utils::getEnumFromObject(env, sTypeEnum);
    env->DeleteLocalRef(sTypeEnum); // release enum reference
    return enumValue;
}

void
VkShaderModuleCreateInfoAccessor::getpCode(VkShaderModuleCreateInfo &clazzInfo) {
    auto pCodeArray = (jintArray) env->GetObjectField(obj, pCodeField);
    if (pCodeArray == nullptr) {
        clazzInfo.codeSize = 0;
        clazzInfo.pCode = nullptr;
        return;
    }
    auto size = env->GetArrayLength(pCodeArray);
    if (size <= 0) {
        clazzInfo.codeSize = 0;
        clazzInfo.pCode = nullptr;
        env->DeleteLocalRef(pCodeArray);
        return;
    }
    auto count = static_cast<std::size_t>(size);
    auto *copy = awake::VulkanAccessArena::current().allocArray<uint32_t>(count);
    env->GetIntArrayRegion(pCodeArray, 0, size, reinterpret_cast<jint *>(copy));
    clazzInfo.codeSize = count * sizeof(uint32_t);
    clazzInfo.pCode = copy;
    env->DeleteLocalRef(pCodeArray);
}

uint32_t
VkShaderModuleCreateInfoAccessor::getflags() {
    return (uint32_t) (jint) env->GetIntField(obj, flagsField); // primitive
}

void
VkShaderModuleCreateInfoAccessor::fromObject(VkShaderModuleCreateInfo &clazzInfo) {
    clazzInfo.sType = getsType(); // Enum VkStructureType
    getpNext(clazzInfo); // Other void*
    clazzInfo.flags = getflags(); // Primitive uint32_t
    getpCode(clazzInfo);  // int Object Array
}

void
VkShaderModuleCreateInfoAccessor::getpNext(VkShaderModuleCreateInfo &clazzInfo) {
    auto ref = (void *) (jobject) env->GetObjectField(obj, pNextField); // Any Object
    clazzInfo.pNext = ref;
}

VkShaderModuleCreateInfoAccessor::~VkShaderModuleCreateInfoAccessor() {
    env->DeleteLocalRef(clazz);
}


/*
 *  VkApplicationInfoAccessor.cpp
 *  Vulkan accessor for VkApplicationInfo
 *  Created by Ron June Valdoz */

#include <includes/VkApplicationInfoAccessor.h>
#include <includes/VulkanAccessArena.h>

VkApplicationInfoAccessor::VkApplicationInfoAccessor(JNIEnv *env, jobject obj) : env(env),
                                                                                 obj(obj) {
    clazz = env->GetObjectClass(obj);
    sTypeField = env->GetFieldID(clazz, "sType",
                                 "Lcom/awakekt/awake/vulkan/enums/VkStructureType;");
    pNextField = env->GetFieldID(clazz, "pNext", "Ljava/lang/Object;");
    pApplicationNameField = env->GetFieldID(clazz, "pApplicationName", "Ljava/lang/String;");
    applicationVersionField = env->GetFieldID(clazz, "applicationVersion", "I");
    pEngineNameField = env->GetFieldID(clazz, "pEngineName", "Ljava/lang/String;");
    engineVersionField = env->GetFieldID(clazz, "engineVersion", "I");
    apiVersionField = env->GetFieldID(clazz, "apiVersion", "I");
}

VkStructureType
VkApplicationInfoAccessor::getsType() {
    auto sTypeEnum = (jobject) env->GetObjectField(obj, sTypeField);
    auto enumValue = (VkStructureType) enum_utils::getEnumFromObject(env, sTypeEnum);
    env->DeleteLocalRef(sTypeEnum); // release enum reference
    return enumValue;
}

uint32_t
VkApplicationInfoAccessor::getapplicationVersion() {
    return (uint32_t) (jint) env->GetIntField(obj, applicationVersionField); // primitive
}

void
VkApplicationInfoAccessor::fromObject(VkApplicationInfo &clazzInfo) {
    clazzInfo.sType = getsType(); // Enum VkStructureType
    getpNext(clazzInfo); // Other void*
    getpApplicationName(clazzInfo); // Other const char*
    clazzInfo.applicationVersion = getapplicationVersion(); // Primitive uint32_t
    getpEngineName(clazzInfo); // Other const char*
    clazzInfo.engineVersion = getengineVersion(); // Primitive uint32_t
    clazzInfo.apiVersion = getapiVersion(); // Primitive uint32_t
}

void
VkApplicationInfoAccessor::getpNext(VkApplicationInfo &clazzInfo) {
    auto ref = (void *) (jobject) env->GetObjectField(obj, pNextField); // Any Object
    clazzInfo.pNext = ref;
}

void
VkApplicationInfoAccessor::getpApplicationName(VkApplicationInfo &clazzInfo) {
    auto pApplicationNameObj = (jstring) env->GetObjectField(obj, pApplicationNameField);
    clazzInfo.pApplicationName = awake::VulkanAccessArena::current().copyJString(env, pApplicationNameObj);
    if (pApplicationNameObj != nullptr) {
        env->DeleteLocalRef(pApplicationNameObj);
    }
}

uint32_t
VkApplicationInfoAccessor::getapiVersion() {
    return (uint32_t) (jint) env->GetIntField(obj, apiVersionField); // primitive
}

uint32_t
VkApplicationInfoAccessor::getengineVersion() {
    return (uint32_t) (jint) env->GetIntField(obj, engineVersionField); // primitive
}

void
VkApplicationInfoAccessor::getpEngineName(VkApplicationInfo &clazzInfo) {
    auto pEngineNameObj = (jstring) env->GetObjectField(obj, pEngineNameField);
    clazzInfo.pEngineName = awake::VulkanAccessArena::current().copyJString(env, pEngineNameObj);
    if (pEngineNameObj != nullptr) {
        env->DeleteLocalRef(pEngineNameObj);
    }
}

VkApplicationInfoAccessor::~VkApplicationInfoAccessor() {
    env->DeleteLocalRef(clazz);
}


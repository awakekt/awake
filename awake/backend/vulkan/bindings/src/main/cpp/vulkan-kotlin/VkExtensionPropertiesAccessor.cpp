/*
 *  VkExtensionPropertiesAccessor.cpp
 *  Vulkan accessor for VkExtensionProperties
 *  Created by Ron June Valdoz */

#include <includes/VkExtensionPropertiesAccessor.h>
#include <cstring>

VkExtensionPropertiesAccessor::VkExtensionPropertiesAccessor(JNIEnv *env, jobject obj) : env(env),
                                                                                         obj(obj) {
    clazz = env->GetObjectClass(obj);
    extensionNameField = env->GetFieldID(clazz, "extensionName", "Ljava/lang/String;");
    specVersionField = env->GetFieldID(clazz, "specVersion", "I");
}

void
VkExtensionPropertiesAccessor::fromObject(VkExtensionProperties &clazzInfo) {
    getextensionName(clazzInfo); // Other const char*
    clazzInfo.specVersion = getspecVersion(); // Primitive uint32_t
}

void
VkExtensionPropertiesAccessor::getextensionName(VkExtensionProperties &clazzInfo) {
    auto extensionNameObj = (jstring) env->GetObjectField(obj, extensionNameField);
    if (extensionNameObj == nullptr) {
        clazzInfo.extensionName[0] = '\0';
        return;
    }
    auto str = env->GetStringUTFChars(extensionNameObj, nullptr);
    if (str != nullptr) {
        std::strncpy(clazzInfo.extensionName, str, VK_MAX_EXTENSION_NAME_SIZE - 1);
        clazzInfo.extensionName[VK_MAX_EXTENSION_NAME_SIZE - 1] = '\0';
        env->ReleaseStringUTFChars(extensionNameObj, str);
    } else {
        clazzInfo.extensionName[0] = '\0';
    }
    env->DeleteLocalRef(extensionNameObj);
}

uint32_t
VkExtensionPropertiesAccessor::getspecVersion() {
    return (uint32_t) (jint) env->GetIntField(obj, specVersionField); // primitive
}

VkExtensionPropertiesAccessor::~VkExtensionPropertiesAccessor() {
    env->DeleteLocalRef(clazz);
}


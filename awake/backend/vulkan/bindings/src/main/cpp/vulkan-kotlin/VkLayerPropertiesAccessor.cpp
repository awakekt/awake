/*
 *  VkLayerPropertiesAccessor.cpp
 *  Vulkan accessor for VkLayerProperties
 *  Created by Ron June Valdoz */

#include <includes/VkLayerPropertiesAccessor.h>
#include <cstring>

VkLayerPropertiesAccessor::VkLayerPropertiesAccessor(JNIEnv *env, jobject obj) : env(env),
                                                                                 obj(obj) {
    clazz = env->GetObjectClass(obj);
    layerNameField = env->GetFieldID(clazz, "layerName", "Ljava/lang/String;");
    specVersionField = env->GetFieldID(clazz, "specVersion", "I");
    implementationVersionField = env->GetFieldID(clazz, "implementationVersion", "I");
    descriptionField = env->GetFieldID(clazz, "description", "Ljava/lang/String;");
}

uint32_t
VkLayerPropertiesAccessor::getspecVersion() {
    return (uint32_t) (jint) env->GetIntField(obj, specVersionField); // primitive
}

void
VkLayerPropertiesAccessor::fromObject(VkLayerProperties &clazzInfo) {
    getlayerName(clazzInfo); // Other const char*
    clazzInfo.specVersion = getspecVersion(); // Primitive uint32_t
    clazzInfo.implementationVersion = getimplementationVersion(); // Primitive uint32_t
    getdescription(clazzInfo); // Other const char*
}

void
VkLayerPropertiesAccessor::getlayerName(VkLayerProperties &clazzInfo) {
    auto layerNameObj = (jstring) env->GetObjectField(obj, layerNameField);
    if (layerNameObj == nullptr) {
        clazzInfo.layerName[0] = '\0';
        return;
    }
    auto str = env->GetStringUTFChars(layerNameObj, nullptr);
    if (str != nullptr) {
        std::strncpy(clazzInfo.layerName, str, VK_MAX_EXTENSION_NAME_SIZE - 1);
        clazzInfo.layerName[VK_MAX_EXTENSION_NAME_SIZE - 1] = '\0';
        env->ReleaseStringUTFChars(layerNameObj, str);
    } else {
        clazzInfo.layerName[0] = '\0';
    }
    env->DeleteLocalRef(layerNameObj);
}

uint32_t
VkLayerPropertiesAccessor::getimplementationVersion() {
    return (uint32_t) (jint) env->GetIntField(obj, implementationVersionField); // primitive
}

void
VkLayerPropertiesAccessor::getdescription(VkLayerProperties &clazzInfo) {
    auto descriptionObj = (jstring) env->GetObjectField(obj, descriptionField);
    if (descriptionObj == nullptr) {
        clazzInfo.description[0] = '\0';
        return;
    }
    auto str = env->GetStringUTFChars(descriptionObj, nullptr);
    if (str != nullptr) {
        std::strncpy(clazzInfo.description, str, VK_MAX_DESCRIPTION_SIZE - 1);
        clazzInfo.description[VK_MAX_DESCRIPTION_SIZE - 1] = '\0';
        env->ReleaseStringUTFChars(descriptionObj, str);
    } else {
        clazzInfo.description[0] = '\0';
    }
    env->DeleteLocalRef(descriptionObj);
}

VkLayerPropertiesAccessor::~VkLayerPropertiesAccessor() {
    env->DeleteLocalRef(clazz);
}


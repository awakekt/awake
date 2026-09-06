/*
 *  VkPhysicalDevicePropertiesMutator.cpp
 *  Vulkan mutator for VkPhysicalDeviceProperties
 *  Created by Ron June Valdoz */

#include <includes/VkPhysicalDevicePropertiesMutator.h>

VkPhysicalDevicePropertiesMutator::VkPhysicalDevicePropertiesMutator(JNIEnv *env) : env(env) {
    this->env = env;
    clazz = env->FindClass(
            "com/awakekt/awake/vulkan/models/physicaldevice/VkPhysicalDeviceProperties");
    apiVersionField = env->GetFieldID(clazz, "apiVersion", "I");
    driverVersionField = env->GetFieldID(clazz, "driverVersion", "I");
    vendorIDField = env->GetFieldID(clazz, "vendorID", "I");
    deviceIDField = env->GetFieldID(clazz, "deviceID", "I");
    deviceTypeField = env->GetFieldID(clazz, "deviceType",
                                      "Lcom/awakekt/awake/vulkan/enums/VkPhysicalDeviceType;");
    deviceNameField = env->GetFieldID(clazz, "deviceName", "[C");
    pipelineCacheUUIDField = env->GetFieldID(clazz, "pipelineCacheUUID", "[B");
    limitsField = env->GetFieldID(clazz, "limits",
                                  "Lcom/awakekt/awake/vulkan/models/physicaldevice/VkPhysicalDeviceLimits;");
    sparsePropertiesField = env->GetFieldID(clazz, "sparseProperties",
                                            "Lcom/awakekt/awake/vulkan/models/physicaldevice/VkPhysicalDeviceSparseProperties;");
}

jobject
VkPhysicalDevicePropertiesMutator::toObject(VkPhysicalDeviceProperties source) {
    auto constructor = env->GetMethodID(clazz, "<init>", "()V");
    auto newObj = env->NewObject(clazz, constructor);
    env->SetIntField(newObj, apiVersionField, static_cast<jint>(source.apiVersion));
    env->SetIntField(newObj, driverVersionField, static_cast<jint>(source.driverVersion));
    env->SetIntField(newObj, vendorIDField, static_cast<jint>(source.vendorID));
    env->SetIntField(newObj, deviceIDField, static_cast<jint>(source.deviceID));
    auto deviceType = enum_utils::setEnumFromVulkan(env, static_cast<jint>(source.deviceType),
                                                    "com/awakekt/awake/vulkan/enums/VkPhysicalDeviceType");
    env->SetObjectField(newObj, deviceTypeField, deviceType);
    env->DeleteLocalRef(deviceType);
    // processing primitive array
    jcharArray deviceName = env->NewCharArray(VK_MAX_PHYSICAL_DEVICE_NAME_SIZE);
    env->SetCharArrayRegion(deviceName, 0, VK_MAX_PHYSICAL_DEVICE_NAME_SIZE,
                            reinterpret_cast<jchar *>(source.deviceName ));
    env->SetObjectField(newObj, deviceNameField, deviceName);
    env->DeleteLocalRef(deviceName);
    // processing primitive array
    jbyteArray pipelineCacheUUID = env->NewByteArray(VK_UUID_SIZE);
    env->SetByteArrayRegion(pipelineCacheUUID, 0, VK_UUID_SIZE,
                            reinterpret_cast<jbyte *>(source.pipelineCacheUUID ));
    env->SetObjectField(newObj, pipelineCacheUUIDField, pipelineCacheUUID);
    env->DeleteLocalRef(pipelineCacheUUID);
    VkPhysicalDeviceLimitsMutator limitsMutator(env);
    auto limits = limitsMutator.toObject(source.limits);
    env->SetObjectField(newObj, limitsField, limits);
    env->DeleteLocalRef(limits);
    VkPhysicalDeviceSparsePropertiesMutator sparsePropertiesMutator(env);
    auto sparseProperties = sparsePropertiesMutator.toObject(source.sparseProperties);
    env->SetObjectField(newObj, sparsePropertiesField, sparseProperties);
    env->DeleteLocalRef(sparseProperties);
    return newObj;
}

VkPhysicalDevicePropertiesMutator::~VkPhysicalDevicePropertiesMutator() {
    // env->DeleteGlobalRef(clazz);
}


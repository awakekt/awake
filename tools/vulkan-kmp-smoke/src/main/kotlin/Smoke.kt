/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import io.github.awakelab.awake.vulkan.Version
import io.github.awakelab.awake.vulkan.Version.Companion.vkVersion
import io.github.awakelab.awake.vulkan.Vulkan
import io.github.awakelab.awake.vulkan.enums.VkCommandBufferLevel
import io.github.awakelab.awake.vulkan.enums.VkQueueFlagBits
import io.github.awakelab.awake.vulkan.enums.VkResult
import io.github.awakelab.awake.vulkan.models.info.VkApplicationInfo
import io.github.awakelab.awake.vulkan.models.info.VkCommandBufferAllocateInfo
import io.github.awakelab.awake.vulkan.models.info.VkCommandPoolCreateInfo
import io.github.awakelab.awake.vulkan.models.info.VkDeviceCreateInfo
import io.github.awakelab.awake.vulkan.models.info.VkDeviceQueueCreateInfo
import io.github.awakelab.awake.vulkan.models.info.VkInstanceCreateInfo

/**
 * Self-contained sample demonstrating pure Vulkan in Kotlin Multiplatform:
 * - Instance creation with portability enumeration (macOS/MoltenVK support)
 * - Physical device enumeration & property inspection
 * - Graphics queue family discovery
 * - Logical device & queue creation
 * - Command pool & command buffer allocation
 * - Clean resource destruction
 */
fun main() {
    println("=== Vulkan KMP Sample ===")

    // 1. Application & Instance Setup
    val appInfo = VkApplicationInfo(
        pApplicationName = "VulkanKmpDemo",
        applicationVersion = Version(1, 0, 0).vkVersion,
        pEngineName = "none",
        engineVersion = Version(1, 0, 0).vkVersion,
        apiVersion = Version(1, 3, 0).vkVersion,
    )

    val enumeratePortabilityFlag = 0x00000001
    val instance = Vulkan.vkCreateInstance(
        VkInstanceCreateInfo(
            pApplicationInfo = arrayOf(appInfo),
            flags = enumeratePortabilityFlag,
            ppEnabledExtensionNames = arrayOf("VK_KHR_portability_enumeration"),
        ),
    )
    println("1. VkInstance created: handle=$instance")

    // 2. Physical Device Enumeration
    val physicalDevices = Vulkan.vkEnumeratePhysicalDevices(instance)
    println("2. Found ${physicalDevices.size} physical device(s)")
    check(physicalDevices.isNotEmpty()) { "No Vulkan-compatible GPU found" }

    val physicalDevice = physicalDevices.first()
    val properties = Vulkan.vkGetPhysicalDeviceProperties(physicalDevice)
    val rawBytes = ByteArray(properties.deviceName.size * 2) { i ->
        val ch = properties.deviceName[i / 2].code
        if (i % 2 == 0) (ch and 0xFF).toByte() else ((ch ushr 8) and 0xFF).toByte()
    }
    val deviceName = rawBytes.decodeToString().substringBefore('\u0000')
    println("   Selected GPU: $deviceName (${properties.deviceType})")

    // 3. Queue Family Discovery
    val queueFamilies = Vulkan.vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice)
    val graphicsQueueFamilyIndex = queueFamilies.indexOfFirst {
        (it.queueFlags and VkQueueFlagBits.VK_QUEUE_GRAPHICS_BIT.value) != 0
    }.also { check(it >= 0) { "No graphics queue family found" } }
    println("3. Graphics queue family index: $graphicsQueueFamilyIndex")

    // 4. Logical Device Creation
    val availableDeviceExtensions = Vulkan.vkEnumerateDeviceExtensionProperties(physicalDevice)
        .map { it.extensionName }
    val enabledDeviceExtensions = mutableListOf<String>()
    if ("VK_KHR_portability_subset" in availableDeviceExtensions) {
        enabledDeviceExtensions.add("VK_KHR_portability_subset")
    }

    val queueCreateInfo = VkDeviceQueueCreateInfo(
        queueFamilyIndex = graphicsQueueFamilyIndex,
        queueCount = 1,
        pQueuePriorities = floatArrayOf(1.0f),
    )
    val features = Vulkan.vkGetPhysicalDeviceFeatures(physicalDevice)
    val deviceCreateInfo = VkDeviceCreateInfo(
        pQueueCreateInfos = arrayOf(queueCreateInfo),
        pEnabledFeatures = arrayOf(features),
        ppEnabledExtensionNames = if (enabledDeviceExtensions.isNotEmpty()) enabledDeviceExtensions.toTypedArray() else null,
    )
    val device = Vulkan.vkCreateDevice(physicalDevice, deviceCreateInfo)
    println("4. VkDevice created: handle=$device")

    val queue = Vulkan.vkGetDeviceQueue(device, graphicsQueueFamilyIndex, 0)
    println("   VkQueue retrieved: handle=$queue")

    // 5. Command Pool & Command Buffer Allocation
    val commandPool = Vulkan.vkCreateCommandPool(
        device,
        VkCommandPoolCreateInfo(queueFamilyIndex = graphicsQueueFamilyIndex),
    )
    println("5. VkCommandPool created: handle=$commandPool")

    val commandBuffer = Vulkan.vkAllocateCommandBuffers(
        device,
        VkCommandBufferAllocateInfo(
            commandPool = commandPool,
            level = VkCommandBufferLevel.VK_COMMAND_BUFFER_LEVEL_PRIMARY,
            commandBufferCount = 1,
        ),
    )
    println("   Allocated VkCommandBuffer: handle=$commandBuffer")

    // 6. Cleanup
    Vulkan.vkDestroyCommandPool(device, commandPool)
    println("6. VkCommandPool destroyed")

    Vulkan.vkDestroyDevice(device)
    println("7. VkDevice destroyed")

    Vulkan.vkDestroyInstance(instance)
    println("8. VkInstance destroyed")

    println("=== Vulkan KMP Lifecycle Complete: ${VkResult.VK_SUCCESS} ===")
}

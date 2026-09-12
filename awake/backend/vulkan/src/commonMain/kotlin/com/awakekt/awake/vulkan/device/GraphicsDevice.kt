/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.device

import com.awakekt.awake.vulkan.Version
import com.awakekt.awake.vulkan.Version.Companion.vkVersion
import com.awakekt.awake.vulkan.Vulkan
import com.awakekt.awake.vulkan.createSurface
import com.awakekt.awake.vulkan.destroySurfaceWindow
import com.awakekt.awake.vulkan.enums.VkPhysicalDeviceType
import com.awakekt.awake.vulkan.enums.flags.VkDebugUtilsMessageSeverityFlagBitsEXT
import com.awakekt.awake.vulkan.gen.VulkanWindow
import com.awakekt.awake.vulkan.models.info.VkApplicationInfo
import com.awakekt.awake.vulkan.models.info.VkDeviceCreateInfo
import com.awakekt.awake.vulkan.models.info.VkDeviceQueueCreateInfo
import com.awakekt.awake.vulkan.models.info.VkInstanceCreateInfo
import com.awakekt.awake.vulkan.models.info.debug.DebugUtilsFormattedCallback
import com.awakekt.awake.vulkan.models.info.debug.VkDebugUtilsMessengerCreateInfoEXT
import com.awakekt.awake.vulkan.models.physicaldevice.VkPhysicalDevice
import com.awakekt.awake.vulkan.utils.QueueFamilyIndices
import com.awakekt.awake.vulkan.utils.findQueueFamilies
import com.awakekt.awake.vulkan.utils.getAppExtProps
import com.awakekt.awake.vulkan.utils.getAppLayerProps

/**
 * Phase 2 (renderer abstraction): owns the instance/surface/physical-device/logical-device/
 * queue lifecycle that used to be four separate private functions
 * (`createInstance`/`pickPhysicalDevice`/`createLogicalDevice`/`setupDebugMessenger`) plus
 * seven scattered fields directly on the demo's `VulkanApplication`. Extracted verbatim --
 * same calls, same order, same hazards already discovered and documented (MoltenVK
 * Portability detection, queue-family completeness check) -- so this is a structural move,
 * not a behavior change.
 */
class GraphicsDevice {
    var instance: Long = 0
    var debugUtilsMessenger: Long = 0
    var surface: Long = 0
    var physicalDevice: Long = 0
    var device: Long = 0
    var graphicsQueue: Long = 0
    var presentQueue: Long = 0

    private var nativeWindow: Any? = null
    private var failOnValidationError = false
    private val validationErrors = mutableListOf<String>()

    /** [window] is an `android.view.Surface` on Android, or a GLFW window handle (`Long`)
     * on desktop -- see [com.awakekt.awake.vulkan.createSurface]. */
    fun create(window: Any) {
        createInstance()
        setupDebugMessenger()
        nativeWindow = window
        surface = createSurface(instance, window)
        pickPhysicalDevice()
        val indices = findQueueFamilies(physicalDevice, surface)
        if (!indices.isComplete()) {
            // graphics not supported?
            throw Exception("GPU graphics / Presentation not supported")
        }
        createLogicalDevice(indices)
    }

    /** Desktop-only headless variant of [create] for pure offscreen rendering (no window, no
     * `VkSurfaceKHR`, no swapchain -- see `Renderer`'s createHeadless doc comment and
     * docs/reference/decision-log.md's pixel-baseline-testing entry). `surface` stays `0L`
     * (`VK_NULL_HANDLE`) for this instance's whole lifetime: [findQueueFamilies] already
     * treats that as "skip present-family detection" (see its own doc comment), so only a
     * graphics-capable queue family is required here, and that same family backs both
     * [graphicsQueue]/[presentQueue] (the latter is never actually used without a swapchain
     * to present to). */
    fun createHeadless() {
        failOnValidationError = true
        createInstance(includeGlfwExtensions = false)
        setupDebugMessenger()
        pickPhysicalDevice()
        val graphicsFamily = requireNotNull(findQueueFamilies(physicalDevice, surface).graphicsFamily) {
            "GPU graphics queue not supported"
        }
        createLogicalDevice(QueueFamilyIndices(graphicsFamily, graphicsFamily))
    }

    /** [includeGlfwExtensions] is `false` for [createHeadless]: `glfwGetRequiredInstanceExtensions`
     * requires `glfwInit()` to have already run, which is NOT safe to call from a plain JVM test
     * thread on macOS (Cocoa requires GLFW's whole lifecycle on the real OS main thread -- see
     * `GlfwManualVerify.kt`'s doc comment; confirmed empirically for `glfwCreateWindow`, and not
     * worth risking for `glfwInit` either) -- a headless [GraphicsDevice] never creates a window
     * or surface, so it doesn't need GLFW's platform-surface instance extensions anyway. */
    private fun createInstance(includeGlfwExtensions: Boolean = true) {
        val appInfo = VkApplicationInfo(
            pApplicationName = "Awake Vulkan - Application",
            pEngineName = "Awake Vulkan - Engine",
            apiVersion = Version(1, 3, 0).vkVersion,
        )
        val layerProperties = selectInstanceLayers(getAppLayerProps())
        val layerExtProps = layerProperties.map { layer ->
            getAppExtProps(layer)
        }.flatten()
        val baseExtProperties = (getAppExtProps() + layerExtProps).distinct()

        // glfwGetRequiredInstanceExtensions() is a safe no-op returning emptyArray() on
        // every non-GLFW platform (Android/iOS) -- see VulkanWindow.kt's actuals.
        val glfwExtensions = if (includeGlfwExtensions) VulkanWindow.glfwGetRequiredInstanceExtensions().toList() else emptyList()
        // MoltenVK (desktop macOS) conforms to the Vulkan Portability spec: vkCreateInstance
        // requires both VK_KHR_portability_enumeration enabled AND
        // VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR set, or it fails with
        // VK_ERROR_INCOMPATIBLE_DRIVER. Detected via baseExtProperties (available even to
        // createHeadless, unlike GLFW's required-extensions list) reporting the extension.
        val onMoltenVk = "VK_KHR_portability_enumeration" in baseExtProperties
        val portabilityExtension = if (onMoltenVk) listOf("VK_KHR_portability_enumeration") else emptyList()
        val instanceFlags = if (onMoltenVk) 0x00000001 else 0 // VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR

        val extProperties = (baseExtProperties + glfwExtensions + portabilityExtension).distinct()

        val createInfo = VkInstanceCreateInfo(
            flags = instanceFlags,
            pApplicationInfo = arrayOf(appInfo),
            ppEnabledLayerNames = layerProperties.toTypedArray(),
            ppEnabledExtensionNames = extProperties.toTypedArray(),
        )
        instance = Vulkan.vkCreateInstance(createInfo)
    }

    private fun setupDebugMessenger() {
        val androidLogCallback: (String, String) -> Unit = { severity, message ->
            println("AWAKE_VERIFY_VALIDATION [$severity] $message")
        }
        val createInfo = VkDebugUtilsMessengerCreateInfoEXT(
            pfnUserCallback = { severity, messageType, callbackData, userData ->
                DebugUtilsFormattedCallback(androidLogCallback).invoke(
                    severity,
                    messageType,
                    callbackData,
                    userData,
                )
                if (severity.isValidationError()) {
                    validationErrors += callbackData.pMessage
                }
                false
            },
            pUserData = null,
        )
        debugUtilsMessenger = Vulkan.vkCreateDebugUtilsMessengerEXT(instance, createInfo)
    }

    private fun pickPhysicalDevice() {
        val physicalDevices =
            Vulkan.vkEnumeratePhysicalDevices(instance).map { VkPhysicalDevice(it, instance) }
        if (physicalDevices.isNotEmpty()) {
            // Pick the best available GPU, falling back to CPU (e.g. lavapipe in headless CI)
            val gpu = physicalDevices
                .filter { vkDevice ->
                    val properties = Vulkan.vkGetPhysicalDeviceProperties(vkDevice.physicalDevice)
                    properties.deviceType in setOf(
                        VkPhysicalDeviceType.VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU,
                        VkPhysicalDeviceType.VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU,
                        VkPhysicalDeviceType.VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU,
                        VkPhysicalDeviceType.VK_PHYSICAL_DEVICE_TYPE_CPU,
                    )
                }
                .minByOrNull { vkDevice ->
                    val properties = Vulkan.vkGetPhysicalDeviceProperties(vkDevice.physicalDevice)
                    when (properties.deviceType) {
                        VkPhysicalDeviceType.VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU -> 0
                        VkPhysicalDeviceType.VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU -> 1
                        VkPhysicalDeviceType.VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU -> 2
                        VkPhysicalDeviceType.VK_PHYSICAL_DEVICE_TYPE_CPU -> 3
                        else -> 4
                    }
                } ?: throw Exception("Cannot find suitable gpu!")
            physicalDevice = gpu.physicalDevice
        }
    }

    private fun createLogicalDevice(indices: QueueFamilyIndices) {
        // to avoid duplicate queue family index use set
        val uniqueQueueFamilies = setOf(
            indices.graphicsFamily!!,
            indices.presentFamily!!,
        )
        val queueInfos = uniqueQueueFamilies.map { uniqueQueueFamilyIndex ->
            VkDeviceQueueCreateInfo(
                queueFamilyIndex = uniqueQueueFamilyIndex,
                queueCount = 1,
                pQueuePriorities = floatArrayOf(1.0f),
            )
        }

        val features = Vulkan.vkGetPhysicalDeviceFeatures(physicalDevice)
        val availableDeviceExtensions =
            Vulkan.vkEnumerateDeviceExtensionProperties(physicalDevice)
                .map { it.extensionName }
                .toSet()
        // Deliberately NOT also querying vkEnumerateDeviceExtensionProperties(physicalDevice,
        // layerName) per-layer (as an earlier version of this function did) -- device-level
        // layers are a deprecated Vulkan 1.0 concept the loader/ICD already ignore in
        // practice (a device's real extension list is layer-independent), and querying them
        // per-layer is actively broken on at least this project's own dev/CI setup: found
        // (2026-07-12, D24 minimap-crash investigation) that as soon as ANY instance layer
        // is discoverable on the host (e.g. `brew install vulkan-validationlayers`, which on
        // macOS ends up scanned even without VK_LAYER_PATH set), this call fails loader-side
        // with "pLayerName is too long or is badly formed", which cascades into
        // `vkCreateDevice` itself failing with VK_ERROR_EXTENSION_NOT_PRESENT -- i.e. the
        // app couldn't even start. Skipping the per-layer query entirely avoids this without
        // losing anything real (the plain, no-layer `deviceExtensions` query above already
        // returns the physical device's actual extension list).
        val requiredDeviceExtensions = buildList {
            if (surface != 0L) add("VK_KHR_swapchain")
            if ("VK_KHR_portability_subset" in availableDeviceExtensions) {
                add("VK_KHR_portability_subset")
            }
        }
        val missingDeviceExtensions = requiredDeviceExtensions.filterNot(availableDeviceExtensions::contains)
        require(missingDeviceExtensions.isEmpty()) {
            "Missing Vulkan device extensions: ${missingDeviceExtensions.joinToString()}"
        }

        val deviceInfo = VkDeviceCreateInfo(
            pQueueCreateInfos = queueInfos.toTypedArray(),
            pEnabledFeatures = arrayOf(features),
            ppEnabledExtensionNames = requiredDeviceExtensions.toTypedArray(),
        )
        device = Vulkan.vkCreateDevice(physicalDevice, deviceInfo)

        graphicsQueue = Vulkan.vkGetDeviceQueue(device, indices.graphicsFamily!!, 0)
        presentQueue = Vulkan.vkGetDeviceQueue(device, indices.presentFamily!!, 0)
    }

    fun destroy() {
        if (surface != 0L) Vulkan.vkDestroySurfaceKHR(instance, surface)
        if (device != 0L) Vulkan.vkDestroyDevice(device)
        if (debugUtilsMessenger != 0L) Vulkan.vkDestroyDebugUtilsMessengerEXT(instance, debugUtilsMessenger)
        if (instance != 0L) Vulkan.vkDestroyInstance(instance)
        nativeWindow?.let { destroySurfaceWindow(it) }
        if (failOnValidationError && validationErrors.isNotEmpty()) {
            error(
                "Vulkan validation errors were reported:\n" +
                    validationErrors.joinToString(separator = "\n\n"),
            )
        }
    }

    private fun VkDebugUtilsMessageSeverityFlagBitsEXT.isValidationError(): Boolean =
        this == VkDebugUtilsMessageSeverityFlagBitsEXT.VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT
}

/** The Khronos validation layer, the only instance layer this engine asks for. */
internal const val VALIDATION_LAYER = "VK_LAYER_KHRONOS_validation"

/**
 * Which of the [installed] instance layers to enable: the Khronos validation layer, and nothing
 * else.
 *
 * `createInstance` used to pass `getAppLayerProps()` straight through -- every layer present on
 * the machine, enabled unconditionally, in every build. That is not the same thing as "validation
 * is on": a developer with API-dump, screenshot, frame-capture, or vendor overlay layers installed
 * had all of them injected into every run, which changes timing, output, and sometimes rendering.
 * It also made behavior depend on what a given machine happened to have installed, so a
 * reproduction on one desktop was not a reproduction on another.
 *
 * Naming the one layer we want keeps the existing validation behavior -- including the
 * `failOnValidationError` path headless tests rely on -- and drops the rest. Returns empty when
 * validation is not installed, which is the same instance-creation path as before.
 */
internal fun selectInstanceLayers(installed: List<String>): List<String> =
    installed.filter { it == VALIDATION_LAYER }

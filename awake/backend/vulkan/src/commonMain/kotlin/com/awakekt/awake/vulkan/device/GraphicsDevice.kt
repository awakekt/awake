/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.device

import com.awakekt.awake.core.config.EnvSource
import com.awakekt.awake.core.config.defaultPlatformEnvSource
import com.awakekt.awake.render.passes.uniforms.DrawBlockWriters
import com.awakekt.awake.render.pipeline.GpuDeviceClass
import com.awakekt.awake.render.texture.CompressedTextureFormat
import com.awakekt.awake.vulkan.Version
import com.awakekt.awake.vulkan.Version.Companion.vkVersion
import com.awakekt.awake.vulkan.Vulkan
import com.awakekt.awake.vulkan.createSurface
import com.awakekt.awake.vulkan.enums.VkPhysicalDeviceType
import com.awakekt.awake.vulkan.enums.VkResult
import com.awakekt.awake.vulkan.enums.flags.VkDebugUtilsMessageSeverityFlagBitsEXT
import com.awakekt.awake.vulkan.gen.VulkanCommands
import com.awakekt.awake.vulkan.gen.VulkanFeatures10
import com.awakekt.awake.vulkan.gen.VulkanModernFeatures
import com.awakekt.awake.vulkan.gen.VulkanWindow
import com.awakekt.awake.vulkan.models.info.VkApplicationInfo
import com.awakekt.awake.vulkan.models.info.VkInstanceCreateInfo
import com.awakekt.awake.vulkan.models.info.debug.DebugUtilsFormattedCallback
import com.awakekt.awake.vulkan.models.info.debug.VkDebugUtilsMessengerCreateInfoEXT
import com.awakekt.awake.vulkan.models.physicaldevice.VkPhysicalDevice
import com.awakekt.awake.vulkan.models.physicaldevice.VkPhysicalDeviceFeatures
import com.awakekt.awake.vulkan.models.physicaldevice.VkPhysicalDeviceProperties
import com.awakekt.awake.vulkan.swapchain.SwapchainPacer
import com.awakekt.awake.vulkan.texture.Texture
import com.awakekt.awake.vulkan.utils.QueueFamilyIndices
import com.awakekt.awake.vulkan.utils.VkResultException
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
    /** Serializes host access to Vulkan queues (`vkQueueSubmit` / `vkQueuePresentKHR`) across threads. */
    internal val queueLock = QueueLock()

    /** Reusable persistently-mapped staging buffer shared by static mesh and texture uploads. */
    internal var stagingBufferPool = ReusableStagingBuffer(this)
        private set

    /** Active renderer reaper for frame-safe deferred destruction, or null when no presentable renderer runs. */
    internal var activeReaper: DeferredResourceReaper? = null

    /** Number of resource destructions deferred through [activeReaper] without `vkDeviceWaitIdle`. */
    internal var deferredDestructionCount: Long = 0L

    private var frameUniformBuffers: com.awakekt.awake.vulkan.renderer.FrameUniformBuffers? = null

    /** Per-draw uniform blocks written to material slots so far. For tests and probes. */
    internal var drawBlockWrites = 0L

    /** The writers draws' own blocks are packed with, kept so a moving draw allocates no block per frame. */
    internal val drawBlockWriters = DrawBlockWriters()

    /** Instanced draws' model buffers created so far: one per frame slot, and one each time a slot's grows. */
    internal var instanceBuffersCreated = 0L

    /** Per-draw uniform buffers created so far, one per material uniform slot. */
    internal var uniformBuffersCreated = 0L

    /** Content-feature uniform blocks written to per-frame slots so far. For tests and probes. */
    internal var contentUniformWrites = 0L

    private var nextTextureDescriptorVersion = 1L

    /** Monotonic version tag stamped onto material texture bindings so UI descriptor caching never aliases recycled Vulkan handles. */
    internal fun allocateTextureDescriptorVersion(): Long = nextTextureDescriptorVersion++

    /** The frame block each frame's lit draws bind beside their own; made on first use. */
    internal val frameUniforms: com.awakekt.awake.vulkan.renderer.FrameUniformBuffers
        get() = frameUniformBuffers
            ?: com.awakekt.awake.vulkan.renderer.FrameUniformBuffers(this).also { frameUniformBuffers = it }

    /** The `VkInstance` handle, or 0 until [create] or [createHeadless] has run. */
    var instance: Long = 0

    /**
     * The `VkDebugUtilsMessengerEXT` handle that routes validation output to the log and records
     * errors, or 0 until an instance exists and when the instance has no `VK_EXT_debug_utils`.
     */
    var debugUtilsMessenger: Long = 0

    /** The instance extensions [createInstance] enabled. */
    private var instanceExtensions: Set<String> = emptySet()

    /**
     * The negotiated Vulkan instance API version requested during [create] or [createHeadless].
     */
    var instanceVersion: Version = Version(1, 0, 0)
        private set

    /**
     * Hardware capabilities, supported API versions, and feature paths for this device.
     */
    var capabilities: DeviceCapabilities = DeviceCapabilities.Uninitialized
        private set

    /** The physical device's PCI vendor id, or 0 before [create]: `Renderer.vendorId` (#1700). */
    internal var vendorId: Int = 0
        private set

    /**
     * The `VkSurfaceKHR` presented to. It stays 0 for a headless device, and after [replaceSurface]
     * released it.
     */
    var surface: Long = 0

    /**
     * What paces presents beside the frame loop (#1699), set before [create]: told about the device's
     * extensions, its creation and destruction, the window, and every swapchain built on it.
     */
    internal var swapchainPacer: SwapchainPacer? = null

    /** The `VkPhysicalDevice` handle of the GPU picked by [create], or 0 before that. */
    var physicalDevice: Long = 0

    /** The logical `VkDevice` handle, or 0 before [create] or [createHeadless] has run. */
    var device: Long = 0

    /**
     * The queue, at index 0 of the graphics family, that graphics and upload work is submitted to.
     */
    var graphicsQueue: Long = 0

    /**
     * The queue, at index 0 of the present family, that frames are presented on. For a headless
     * device it is the graphics family's queue and is never used.
     */
    var presentQueue: Long = 0

    /**
     * Nanoseconds per GPU timestamp tick on the graphics queue, and how many bits of a timestamp
     * are valid there. Zero valid bits means the queue cannot time its work.
     */
    internal var timestampPeriodNs: Float = 0f
        private set
    internal var timestampValidBits: Int = 0
        private set

    /** Whether the logical device loaded `VK_KHR_swapchain`, which headless devices do only if offered. */
    internal var swapchainExtensionEnabled = false
        private set

    /** Device-wide `VkPipelineCache` shared across all graphics pipelines created on [device]. */
    internal var sharedPipelineCache: Long = 0L
        private set

    /**
     * Optional serialized `VkPipelineCache` bytes loaded from disk before [create] or
     * [createHeadless] to seed [sharedPipelineCache]. Incompatible or stale driver blobs are
     * safely ignored by Vulkan.
     */
    var initialPipelineCacheData: ByteArray? = null

    /** Effective Vulkan API version `min(instanceVersion, deviceApiVersion)` for this device. */
    internal var effectiveApiVersion: Version = Version(1, 0, 0)
        private set

    /** Bitmask of [VulkanModernFeatures] genuinely enabled on [device] via `pNext` chaining. */
    internal var enabledModernFeatures: Int = 0
        private set

    /**
     * Exports [sharedPipelineCache] as raw bytes suitable for disk persistence, or an empty array
     * before [create] / after [destroy].
     */
    fun exportPipelineCacheData(): ByteArray =
        if (device != 0L && sharedPipelineCache != 0L) {
            VulkanCommands.vkGetPipelineCacheData(device, sharedPipelineCache)
        } else {
            ByteArray(0)
        }

    /**
     * Whether [destroy] throws when the validation layer reported errors. Always on for a headless
     * device; a test presenting through a window turns it on too, or its validation errors are only
     * printed (#1688).
     */
    internal var failOnValidationError = false
    private val validationErrors = mutableListOf<String>()

    /** True only when the created instance actually enabled the Khronos validation layer. */
    internal var validationLayerEnabled = false
        private set

    /** [window] is an `android.view.Surface` on Android, or a GLFW window handle (`Long`)
     * on desktop -- see [com.awakekt.awake.vulkan.createSurface]. */
    fun create(window: Any) {
        createInstance()
        setupDebugMessenger()
        surface = createSurface(instance, window)
        swapchainPacer?.windowChanged(window)
        pickPhysicalDevice()
        val indices = findQueueFamilies(physicalDevice, surface)
        if (!indices.isComplete()) {
            // graphics not supported?
            throw Exception("GPU graphics / Presentation not supported")
        }
        createLogicalDevice(indices)
    }

    /**
     * Swaps the presentation surface for [window] on the same instance and device, or releases it
     * when [window] is null. The caller must have destroyed the swapchain built on the old surface.
     */
    fun replaceSurface(window: Any?) {
        if (surface != 0L) Vulkan.vkDestroySurfaceKHR(instance, surface)
        surface = 0L
        if (window != null) surface = createSurface(instance, window)
        swapchainPacer?.windowChanged(window)
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
        val layers = selectInstanceLayers(getAppLayerProps(), validationRequested())
        instance = try {
            createInstance(layers, includeGlfwExtensions).also {
                validationLayerEnabled = VALIDATION_LAYER in layers
            }
        } catch (e: VkResultException) {
            // A layer the loader lists can still fail to load, e.g. a Homebrew layer seen by an
            // app's bundled loader. Validation is a debugging aid, so start without it.
            if (layers.isEmpty() || e.result != VkResult.VK_ERROR_LAYER_NOT_PRESENT) throw e
            println("Awake/Vulkan: validation was requested but $layers failed to load; continuing without it")
            validationLayerEnabled = false
            createInstance(emptyList(), includeGlfwExtensions)
        }
    }

    private fun createInstance(layerProperties: List<String>, includeGlfwExtensions: Boolean): Long {
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

        instanceExtensions = extProperties.toSet()

        val candidateVersions = listOf(
            Version(1, 4, 0),
            Version(1, 3, 0),
            Version(1, 2, 0),
            Version(1, 1, 0),
            Version(1, 0, 0),
        )

        var lastError: VkResultException? = null
        for (candidate in candidateVersions) {
            val appInfo = VkApplicationInfo(
                pApplicationName = "Awake Vulkan - Application",
                pEngineName = "Awake Vulkan - Engine",
                apiVersion = candidate.vkVersion,
            )
            val createInfo = VkInstanceCreateInfo(
                flags = instanceFlags,
                pApplicationInfo = arrayOf(appInfo),
                ppEnabledLayerNames = layerProperties.toTypedArray(),
                ppEnabledExtensionNames = extProperties.toTypedArray(),
            )
            try {
                val created = Vulkan.vkCreateInstance(createInfo)
                instanceVersion = candidate
                return created
            } catch (e: VkResultException) {
                if (e.result == VkResult.VK_ERROR_INCOMPATIBLE_DRIVER && candidate > Version(1, 0, 0)) {
                    lastError = e
                    continue
                }
                throw e
            }
        }
        val err = lastError
        if (err != null) throw err
        error("Failed to create Vulkan instance")
    }

    private fun setupDebugMessenger() {
        // A driver with no validation layer may not offer VK_EXT_debug_utils, as an emulator's
        // software Vulkan doesn't; there is then no messenger to create, and nothing to route.
        if (DEBUG_UTILS_EXTENSION !in instanceExtensions) return
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
            val properties = Vulkan.vkGetPhysicalDeviceProperties(physicalDevice)
            deviceClass = properties.deviceType.toGpuDeviceClass()
        }
    }

    /** Coarse hardware classification reported by the picked `VkPhysicalDevice`. */
    var deviceClass: GpuDeviceClass = GpuDeviceClass.Unknown
        private set

    /**
     * Whether the device enabled `depthClamp`, so the shadow depth pass can clamp a caster in
     * front of a cascade's near plane onto it instead of clipping it away.
     */
    internal var depthClamp: Boolean = false
        private set

    /**
     * The block formats this device samples. Vulkan guarantees every format of a family a device
     * enables `textureCompressionBC`, `textureCompressionETC2` or `textureCompressionASTC_LDR` for can
     * be sampled with linear filtering, so the features say it without a per-format query.
     */
    internal var compressedTextureFormats: Set<CompressedTextureFormat> = emptySet()
        private set

    /**
     * The most anisotropic samples a sampler on this device can take: the device limit when it offers
     * `samplerAnisotropy`, which [createLogicalDevice] enables with every other feature it reports,
     * and 1 when it does not (#1660).
     */
    internal var maxSamplerAnisotropy: Int = 1
        private set

    /**
     * Every live mipmapped [Texture] on this device, renderer-owned or a content feature's, so a new
     * [com.awakekt.awake.vulkan.renderer.Renderer.maxAnisotropy] reaches them all (#1660).
     */
    internal val sampledTextures = LinkedHashSet<Texture>()

    /** The anisotropic samples a texture created on this device samples with; see [sampledTextures]. */
    internal var anisotropy: Int = 1

    /** Top mip levels a texture uploaded with [Texture.skippingMips] leaves out (#1660). */
    internal var mipSkip: Int = 0

    private fun createLogicalDevice(indices: QueueFamilyIndices) {
        val graphicsFamily = indices.graphicsFamily!!
        val physicalDeviceProperties = Vulkan.vkGetPhysicalDeviceProperties(physicalDevice)
        timestampPeriodNs = physicalDeviceProperties.limits.timestampPeriod
        timestampValidBits = Vulkan.vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice)
            .getOrNull(graphicsFamily)?.timestampValidBits?.toInt() ?: 0
        // to avoid duplicate queue family index use set
        val uniqueQueueFamilies = setOf(
            indices.graphicsFamily!!,
            indices.presentFamily!!,
        )

        val supportedFeatures = Vulkan.vkGetPhysicalDeviceFeatures(physicalDevice)
        val enabledFeatures = selectEnabledDeviceFeatures(supportedFeatures)
        val features10Mask = packFeatures10Mask(enabledFeatures)
        depthClamp = enabledFeatures.depthClamp
        compressedTextureFormats = compressedFormats(enabledFeatures)
        maxSamplerAnisotropy = if (enabledFeatures.samplerAnisotropy) maxOf(1, physicalDeviceProperties.limits.maxSamplerAnisotropy.toInt()) else 1
        val availableDeviceExtensions =
            Vulkan.vkEnumerateDeviceExtensionProperties(physicalDevice)
                .map { it.extensionName }
                .toSet()
        val requiredDeviceExtensions = selectRequiredDeviceExtensions(
            surface = surface,
            physicalDevice = physicalDevice,
            swapchainPacer = swapchainPacer,
            availableDeviceExtensions = availableDeviceExtensions,
        )

        val deviceApiVersion = Version.fromVkVersion(physicalDeviceProperties.apiVersion)
        effectiveApiVersion = minOf(instanceVersion, deviceApiVersion)
        enabledModernFeatures = VulkanCommands.vkQueryModernFeatures(
            instance = instance,
            physicalDevice = physicalDevice,
            apiVersion = effectiveApiVersion.vkVersion,
        )
        device = VulkanCommands.vkCreateLogicalDeviceWithFeatures(
            physicalDevice = physicalDevice,
            queueFamilyIndices = uniqueQueueFamilies.toIntArray(),
            enabledExtensions = requiredDeviceExtensions.toTypedArray(),
            features10Mask = features10Mask,
            apiVersion = effectiveApiVersion.vkVersion,
            modernFeaturesMask = enabledModernFeatures,
        )
        swapchainExtensionEnabled = SWAPCHAIN_EXTENSION in requiredDeviceExtensions
        sharedPipelineCache = VulkanCommands.vkCreatePipelineCacheFromBytes(device, initialPipelineCacheData)

        graphicsQueue = Vulkan.vkGetDeviceQueue(device, indices.graphicsFamily!!, 0)
        presentQueue = Vulkan.vkGetDeviceQueue(device, indices.presentFamily!!, 0)
        swapchainPacer?.deviceCreated(physicalDevice, device, presentQueue, indices.presentFamily!!)

        vendorId = physicalDeviceProperties.vendorID
        capabilities = deriveDeviceCapabilities(
            deviceApiVersion = deviceApiVersion,
            effectiveApiVersion = effectiveApiVersion,
            enabledModernFeatures = enabledModernFeatures,
            properties = physicalDeviceProperties,
            deviceClass = deviceClass,
            availableDeviceExtensions = availableDeviceExtensions,
            enabledExtensions = requiredDeviceExtensions.toSet(),
        )
    }

    /**
     * Destroys the surface, device, debug messenger and instance, in that order, skipping any that
     * were never created.
     *
     * @throws IllegalStateException After teardown, if this device was created headless and the
     * validation layer reported errors; the message lists them.
     */
    fun destroy() {
        if (device != 0L) {
            activeReaper?.let { reaper ->
                com.awakekt.awake.vulkan.gen.VulkanBuffers.vkDeviceWaitIdle(device)
                reaper.destroyAll()
                activeReaper = null
            }
            stagingBufferPool.destroy()
            stagingBufferPool = ReusableStagingBuffer(this)
            frameUniformBuffers?.destroy()
        }
        frameUniformBuffers = null
        if (surface != 0L) Vulkan.vkDestroySurfaceKHR(instance, surface)
        if (sharedPipelineCache != 0L && device != 0L) {
            Vulkan.vkDestroyPipelineCache(device, sharedPipelineCache)
            sharedPipelineCache = 0L
        }
        if (device != 0L) swapchainPacer?.deviceDestroying()
        if (device != 0L) Vulkan.vkDestroyDevice(device)
        if (debugUtilsMessenger != 0L) Vulkan.vkDestroyDebugUtilsMessengerEXT(instance, debugUtilsMessenger)
        if (instance != 0L) Vulkan.vkDestroyInstance(instance)
        capabilities = DeviceCapabilities.Uninitialized
        instanceVersion = Version(1, 0, 0)
        effectiveApiVersion = Version(1, 0, 0)
        enabledModernFeatures = 0
        if (failOnValidationError && validationErrors.isNotEmpty()) {
            error(
                "Vulkan validation errors were reported:\n" +
                    validationErrors.joinToString(separator = "\n\n"),
            )
        }
    }
}

@Suppress("LongParameterList")
private fun deriveDeviceCapabilities(
    deviceApiVersion: Version,
    effectiveApiVersion: Version,
    enabledModernFeatures: Int,
    properties: VkPhysicalDeviceProperties,
    deviceClass: GpuDeviceClass,
    availableDeviceExtensions: Set<String>,
    enabledExtensions: Set<String>,
): DeviceCapabilities {
    val v13 = Version(1, 3, 0)
    val v14 = Version(1, 4, 0)
    fun hasModern(bit: Int): Boolean = (enabledModernFeatures and bit) != 0

    return DeviceCapabilities(
        apiVersion = deviceApiVersion,
        driverVersion = properties.driverVersion,
        deviceName = properties.deviceName.toNullTerminatedString(),
        deviceClass = deviceClass,
        dynamicRendering = hasModern(VulkanModernFeatures.DYNAMIC_RENDERING),
        synchronization2 = hasModern(VulkanModernFeatures.SYNCHRONIZATION_2),
        timelineSemaphores = hasModern(VulkanModernFeatures.TIMELINE_SEMAPHORE),
        extendedDynamicState = effectiveApiVersion >= v13 || EXTENDED_DYNAMIC_STATE_EXTENSION in enabledExtensions,
        extendedDynamicState2 = effectiveApiVersion >= v13 || EXTENDED_DYNAMIC_STATE_2_EXTENSION in enabledExtensions,
        descriptorIndexing = hasModern(VulkanModernFeatures.DESCRIPTOR_INDEXING),
        bufferDeviceAddress = hasModern(VulkanModernFeatures.BUFFER_DEVICE_ADDRESS),
        drawIndirectCount = hasModern(VulkanModernFeatures.DRAW_INDIRECT_COUNT),
        pipelineCreationCacheControl = hasModern(VulkanModernFeatures.PIPELINE_CREATION_CACHE_CONTROL),
        pushDescriptors = supportsCapability(effectiveApiVersion, v14, PUSH_DESCRIPTOR_EXTENSION, enabledExtensions),
        dynamicRenderingLocalRead = supportsCapability(
            effectiveApiVersion,
            v14,
            DYNAMIC_RENDERING_LOCAL_READ_EXTENSION,
            enabledExtensions,
        ),
        maintenance4 = hasModern(VulkanModernFeatures.MAINTENANCE_4),
        maintenance5 = supportsCapability(effectiveApiVersion, v14, MAINTENANCE_5_EXTENSION, enabledExtensions),
        maintenance6 = supportsCapability(effectiveApiVersion, v14, MAINTENANCE_6_EXTENSION, enabledExtensions),
        scalarBlockLayout = hasModern(VulkanModernFeatures.SCALAR_BLOCK_LAYOUT),
        hostImageCopy = HOST_IMAGE_COPY_EXTENSION in availableDeviceExtensions && HOST_IMAGE_COPY_EXTENSION in enabledExtensions,
        lineRasterization = effectiveApiVersion >= v14 ||
            LINE_RASTERIZATION_KHR_EXTENSION in enabledExtensions ||
            LINE_RASTERIZATION_EXT_EXTENSION in enabledExtensions,
        enabledExtensions = enabledExtensions,
    )
}

internal fun packFeatures10Mask(features: VkPhysicalDeviceFeatures): Int {
    var mask = 0
    if (features.fullDrawIndexUint32) mask = mask or VulkanFeatures10.FULL_DRAW_INDEX_UINT32
    if (features.multiDrawIndirect) mask = mask or VulkanFeatures10.MULTI_DRAW_INDIRECT
    if (features.depthClamp) mask = mask or VulkanFeatures10.DEPTH_CLAMP
    if (features.fillModeNonSolid) mask = mask or VulkanFeatures10.FILL_MODE_NON_SOLID
    if (features.samplerAnisotropy) mask = mask or VulkanFeatures10.SAMPLER_ANISOTROPY
    if (features.textureCompressionETC2) mask = mask or VulkanFeatures10.TEXTURE_COMPRESSION_ETC2
    if (features.textureCompressionASTC_LDR) mask = mask or VulkanFeatures10.TEXTURE_COMPRESSION_ASTC_LDR
    if (features.textureCompressionBC) mask = mask or VulkanFeatures10.TEXTURE_COMPRESSION_BC
    return mask
}

private fun selectRequiredDeviceExtensions(
    surface: Long,
    physicalDevice: Long,
    swapchainPacer: SwapchainPacer?,
    availableDeviceExtensions: Set<String>,
): List<String> {
    val required = buildList {
        // Headless too when the device has it: presentable headless images end each frame in
        // PRESENT_SRC_KHR, a layout only this extension defines.
        if (surface != 0L || SWAPCHAIN_EXTENSION in availableDeviceExtensions) add(SWAPCHAIN_EXTENSION)
        if ("VK_KHR_portability_subset" in availableDeviceExtensions) {
            add("VK_KHR_portability_subset")
        }
        // What the pacer asks for, of what the device has (#1699).
        swapchainPacer?.deviceExtensions(physicalDevice, availableDeviceExtensions)
            ?.filter { it in availableDeviceExtensions && it !in this }
            ?.let(::addAll)
    }
    val missing = required.filterNot(availableDeviceExtensions::contains)
    require(missing.isEmpty()) {
        "Missing Vulkan device extensions: ${missing.joinToString()}"
    }
    return required
}

private fun supportsCapability(
    apiVersion: Version,
    minVersion: Version,
    extension: String,
    availableExtensions: Set<String>,
): Boolean = apiVersion >= minVersion || extension in availableExtensions

private fun VkDebugUtilsMessageSeverityFlagBitsEXT.isValidationError(): Boolean =
    this == VkDebugUtilsMessageSeverityFlagBitsEXT.VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT

private const val SWAPCHAIN_EXTENSION = "VK_KHR_swapchain"
private const val DEBUG_UTILS_EXTENSION = "VK_EXT_debug_utils"
private const val EXTENDED_DYNAMIC_STATE_EXTENSION = "VK_EXT_extended_dynamic_state"
private const val EXTENDED_DYNAMIC_STATE_2_EXTENSION = "VK_EXT_extended_dynamic_state2"
private const val PUSH_DESCRIPTOR_EXTENSION = "VK_KHR_push_descriptor"
private const val DYNAMIC_RENDERING_LOCAL_READ_EXTENSION = "VK_KHR_dynamic_rendering_local_read"
private const val MAINTENANCE_5_EXTENSION = "VK_KHR_maintenance5"
private const val MAINTENANCE_6_EXTENSION = "VK_KHR_maintenance6"
private const val HOST_IMAGE_COPY_EXTENSION = "VK_EXT_host_image_copy"
private const val LINE_RASTERIZATION_KHR_EXTENSION = "VK_KHR_line_rasterization"
private const val LINE_RASTERIZATION_EXT_EXTENSION = "VK_EXT_line_rasterization"

/** The Khronos validation layer, the only instance layer this engine asks for. */
internal const val VALIDATION_LAYER = "VK_LAYER_KHRONOS_validation"

/**
 * Whether the Khronos validation layer was asked for, with `-Dawake.vulkan.validation=true` or
 * `AWAKE_VULKAN_VALIDATION=1`.
 *
 * Off unless asked: validation is developer tooling, and a shipped app must not change behavior,
 * or fail to start, because the user's machine has the Vulkan SDK installed. The Gradle `run` task
 * and desktop tests turn it on; `-Pawake.vulkan.validation=false` leaves a `run` task's off.
 */
internal fun validationRequested(env: EnvSource = defaultPlatformEnvSource()): Boolean =
    env.get(VALIDATION_PROPERTY)?.trim()?.lowercase() in setOf("1", "true", "yes", "on")

/** Read as `awake.vulkan.validation` and `AWAKE_VULKAN_VALIDATION`. */
internal const val VALIDATION_PROPERTY = "awake.vulkan.validation"

/**
 * Which of the [installed] instance layers to enable: the Khronos validation layer when
 * [validation] is requested, and nothing else.
 *
 * Only the named layer, never every installed one: API-dump, frame-capture, or vendor overlay
 * layers would otherwise be injected into every run and change timing, output, and sometimes
 * rendering.
 */
internal fun selectInstanceLayers(installed: List<String>, validation: Boolean): List<String> =
    if (validation) installed.filter { it == VALIDATION_LAYER } else emptyList()

private fun CharArray.toNullTerminatedString(): String {
    val bytes = ByteArray(size * 2)
    var byteCount = 0
    var terminated = false
    var i = 0
    while (i < size && !terminated) {
        val c = this[i].code
        val b1 = (c and 0xFF).toByte()
        if (b1 == 0.toByte()) {
            terminated = true
        } else {
            bytes[byteCount++] = b1
            val b2 = ((c ushr 8) and 0xFF).toByte()
            if (b2 == 0.toByte()) {
                terminated = true
            } else {
                bytes[byteCount++] = b2
            }
        }
        i++
    }
    return bytes.decodeToString(0, byteCount)
}

/** The block formats [features] makes samplable: each family its feature enables. */
internal fun compressedFormats(features: VkPhysicalDeviceFeatures): Set<CompressedTextureFormat> = buildSet {
    if (features.textureCompressionBC) addAll(listOf(CompressedTextureFormat.Bc1Rgba, CompressedTextureFormat.Bc3Rgba, CompressedTextureFormat.Bc7Rgba))
    if (features.textureCompressionETC2) add(CompressedTextureFormat.Etc2Rgba8)
    if (features.textureCompressionASTC_LDR) add(CompressedTextureFormat.Astc4x4)
}

/**
 * Enables only the Vulkan 1.0 device features Awake actually uses.
 *
 * Blindly forwarding `vkGetPhysicalDeviceFeatures` turns on `robustBufferAccess` and other
 * unused features that force driver shader bounds-checks and disable fast paths on both mobile
 * and desktop GPUs.
 */
internal fun selectEnabledDeviceFeatures(supported: VkPhysicalDeviceFeatures): VkPhysicalDeviceFeatures =
    VkPhysicalDeviceFeatures(
        fullDrawIndexUint32 = supported.fullDrawIndexUint32,
        multiDrawIndirect = supported.multiDrawIndirect,
        depthClamp = supported.depthClamp,
        fillModeNonSolid = supported.fillModeNonSolid,
        samplerAnisotropy = supported.samplerAnisotropy,
        textureCompressionETC2 = supported.textureCompressionETC2,
        textureCompressionASTC_LDR = supported.textureCompressionASTC_LDR,
        textureCompressionBC = supported.textureCompressionBC,
    )

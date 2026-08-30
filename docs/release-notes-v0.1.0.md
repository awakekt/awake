# Release Notes — vulkan-kmp v0.1.0 (MVP Release)

`vulkan-kmp` is the first Kotlin Multiplatform library providing raw, high-performance Vulkan bindings across **Desktop JVM** (macOS, Linux, Windows), **Android**, and **iOS** (via MoltenVK).

---

## Highlights

### 1. Zero Engine Dependencies
* Focuses strictly on Khronos' Vulkan API surface (`Vk*` structs, handles, enums, functions).
* Designed for graphics engineers and engine authors who need direct Vulkan access in Kotlin Multiplatform without heavyweight framework assumptions.

### 2. Zero-Config Desktop Runtime (`VulkanNativeLoader`)
* On macOS (ARM64/x86_64), Linux (x86_64), and Windows (x86_64), precompiled native libraries are embedded directly inside the desktop JAR.
* At runtime, `VulkanNativeLoader` automatically extracts and loads the host dynamic library to a local cache folder (`~/.awake/natives/<os-arch>/`).
* No manual `-Djava.library.path` or C++ compilation required for consumers.

### 3. Multi-ABI Android Packaging
* Ships companion AAR (`io.github.awake-lab:vulkan-kmp-android-native`) bundling NDK-built JNI libraries for `arm64-v8a` and `x86_64` alongside Khronos validation layer binaries.
* Automatically resolved and packaged into the final APK by AGP.

### 4. iOS Support via MoltenVK
* Preconfigured Kotlin/Native `cinterop` linking against `MoltenVK.xcframework` static library slices for both physical devices (`iosArm64`) and simulator (`iosSimulatorArm64`).

### 5. Binary Compatibility & API Governance
* API signatures locked and tracked with `kotlinx-binary-compatibility-validator` to guarantee binary stability across releases.

---

## Installation

### Gradle (Kotlin DSL)
```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.awake-lab:vulkan-kmp:0.1.0")
}
```

---

## Quickstart

```kotlin
import io.github.awakelab.awake.vulkan.Version
import io.github.awakelab.awake.vulkan.Version.Companion.vkVersion
import io.github.awakelab.awake.vulkan.Vulkan
import io.github.awakelab.awake.vulkan.enums.VkCommandBufferLevel
import io.github.awakelab.awake.vulkan.enums.VkQueueFlagBits
import io.github.awakelab.awake.vulkan.models.info.VkApplicationInfo
import io.github.awakelab.awake.vulkan.models.info.VkCommandBufferAllocateInfo
import io.github.awakelab.awake.vulkan.models.info.VkCommandPoolCreateInfo
import io.github.awakelab.awake.vulkan.models.info.VkDeviceCreateInfo
import io.github.awakelab.awake.vulkan.models.info.VkDeviceQueueCreateInfo
import io.github.awakelab.awake.vulkan.models.info.VkInstanceCreateInfo

fun main() {
    // 1. Initialize Vulkan Instance
    val appInfo = VkApplicationInfo(
        pApplicationName = "MyVulkanApp",
        applicationVersion = Version(1, 0, 0).vkVersion,
        apiVersion = Version(1, 3, 0).vkVersion,
    )

    // On macOS / MoltenVK, portability enumeration is required:
    val enumeratePortabilityFlag = 0x00000001
    val instance = Vulkan.vkCreateInstance(
        VkInstanceCreateInfo(
            pApplicationInfo = arrayOf(appInfo),
            flags = enumeratePortabilityFlag,
            ppEnabledExtensionNames = arrayOf("VK_KHR_portability_enumeration"),
        ),
    )

    // 2. Query Physical GPU and Queue Families
    val physicalDevices = Vulkan.vkEnumeratePhysicalDevices(instance)
    check(physicalDevices.isNotEmpty()) { "No Vulkan-compatible GPU found" }
    val physicalDevice = physicalDevices.first()

    val queueFamilies = Vulkan.vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice)
    val graphicsQueueIndex = queueFamilies.indexOfFirst {
        (it.queueFlags and VkQueueFlagBits.VK_QUEUE_GRAPHICS_BIT.value) != 0
    }

    // 3. Create Logical Device & Queue
    val queueCreateInfo = VkDeviceQueueCreateInfo(
        queueFamilyIndex = graphicsQueueIndex,
        queueCount = 1,
        pQueuePriorities = floatArrayOf(1.0f),
    )
    val features = Vulkan.vkGetPhysicalDeviceFeatures(physicalDevice)
    val device = Vulkan.vkCreateDevice(
        physicalDevice,
        VkDeviceCreateInfo(
            pQueueCreateInfos = arrayOf(queueCreateInfo),
            pEnabledFeatures = arrayOf(features),
        ),
    )
    val queue = Vulkan.vkGetDeviceQueue(device, graphicsQueueIndex, 0)

    // 4. Allocate Command Buffer
    val commandPool = Vulkan.vkCreateCommandPool(
        device,
        VkCommandPoolCreateInfo(queueFamilyIndex = graphicsQueueIndex),
    )
    val commandBuffer = Vulkan.vkAllocateCommandBuffers(
        device,
        VkCommandBufferAllocateInfo(
            commandPool = commandPool,
            level = VkCommandBufferLevel.VK_COMMAND_BUFFER_LEVEL_PRIMARY,
            commandBufferCount = 1,
        ),
    )
    println("Vulkan initialized successfully! Device: $device, CommandBuffer: $commandBuffer")

    // 5. Clean up
    Vulkan.vkDestroyCommandPool(device, commandPool)
    Vulkan.vkDestroyDevice(device)
    Vulkan.vkDestroyInstance(instance)
}
```

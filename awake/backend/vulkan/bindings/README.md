# Vulkan KMP Bindings (`vulkan-kmp`)

Pure, high-performance Vulkan API bindings for Kotlin Multiplatform (Desktop JVM, Android, iOS).

## Installation

### 1. Add Dependency in `build.gradle.kts`

#### Multiplatform Project (`commonMain`)
```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.awake-lab:vulkan-kmp:<version>")
        }
    }
}
```

#### Version Catalog (`gradle/libs.versions.toml`)
```toml
[versions]
vulkan-kmp = "<version>"

[libraries]
vulkan-kmp = { module = "io.github.awake-lab:vulkan-kmp", version.ref = "vulkan-kmp" }
```

```kotlin
// In your module build.gradle.kts:
dependencies {
    implementation(libs.vulkan.kmp)
}
```

---

## Target-Specific Setup & Capabilities

### 🖥️ Desktop JVM (macOS arm64/x86_64, Linux x86_64)
* **Zero Runtime Configuration**: the native library for each supported platform is precompiled and
  bundled inside the desktop JAR. A host compiles only its own, so a release assembles them from one
  CI runner per platform; `verifyDesktopNatives` fails the publish if any is missing.
* **Windows is not shipped.** No CI job has compiled the desktop C++ on it, so it is unproven rather
  than merely untested. A Windows consumer gets a clear "not found for platform" error rather than a
  silent failure.
* At runtime, `VulkanNativeLoader` extracts the library matching the current OS and architecture to
  `~/.awake/natives/<os-arch>/<fingerprint>/` and loads it. The fingerprint is a hash of the
  library's own bytes, so upgrading cannot be shadowed by a copy cached from an earlier version.
* **Developer Override (Optional)**: If you are testing custom native builds, setting `-Djava.library.path=/path/to/libs` takes precedence over embedded extraction.

### 📱 Android
* Supported ABIs: `arm64-v8a`, `x86_64`
* Minimum SDK: `24`
* `vulkan-kmp` automatically pulls in `vulkan-kmp-android-native` AAR transitively, providing JNI bindings and Khronos validation layers. AGP packages the native `.so` files into your APK with zero extra configuration.

### 🍏 iOS (via MoltenVK)
* Supported Targets: `iosArm64` (device) and `iosSimulatorArm64` (simulator)
* Ships `cinterop` metadata and headers linking to `MoltenVK.xcframework`.
* Ensure your Xcode project links against `Metal.framework` and `QuartzCore.framework`.

---

## Complete Vulkan Lifecycle Sample

A pure Kotlin Multiplatform sample demonstrating:
* `VkInstance` creation (with MoltenVK portability enumeration)
* Physical device discovery & GPU inspection
* Graphics queue family detection
* `VkDevice` and `VkQueue` initialization
* `VkCommandPool` creation & `VkCommandBuffer` allocation
* Clean resource teardown

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
    // 1. Application & Instance Setup
    val appInfo = VkApplicationInfo(
        pApplicationName = "VulkanKmpDemo",
        applicationVersion = Version(1, 0, 0).vkVersion,
        apiVersion = Version(1, 3, 0).vkVersion,
    )

    // MoltenVK on macOS requires VK_KHR_portability_enumeration
    val enumeratePortabilityFlag = 0x00000001
    val instance = Vulkan.vkCreateInstance(
        VkInstanceCreateInfo(
            pApplicationInfo = arrayOf(appInfo),
            flags = enumeratePortabilityFlag,
            ppEnabledExtensionNames = arrayOf("VK_KHR_portability_enumeration"),
        ),
    )
    println("1. VkInstance created: handle=$instance")

    // 2. Enumerate Physical Devices (GPUs)
    val physicalDevices = Vulkan.vkEnumeratePhysicalDevices(instance)
    check(physicalDevices.isNotEmpty()) { "No Vulkan-compatible GPU found" }
    val physicalDevice = physicalDevices.first()

    val properties = Vulkan.vkGetPhysicalDeviceProperties(physicalDevice)
    val rawBytes = ByteArray(properties.deviceName.size * 2) { i ->
        val ch = properties.deviceName[i / 2].code
        if (i % 2 == 0) (ch and 0xFF).toByte() else ((ch ushr 8) and 0xFF).toByte()
    }
    val deviceName = rawBytes.decodeToString().substringBefore('\u0000')
    println("2. Selected GPU: $deviceName (${properties.deviceType})")

    // 3. Find Graphics Queue Family
    val queueFamilies = Vulkan.vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice)
    val graphicsQueueFamilyIndex = queueFamilies.indexOfFirst {
        (it.queueFlags and VkQueueFlagBits.VK_QUEUE_GRAPHICS_BIT.value) != 0
    }.also { check(it >= 0) { "No graphics queue family found" } }

    // 4. Create Logical Device
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
    val queue = Vulkan.vkGetDeviceQueue(device, graphicsQueueFamilyIndex, 0)
    println("3. VkDevice created: handle=$device, queue=$queue")

    // 5. Create Command Pool & Allocate Command Buffer
    val commandPool = Vulkan.vkCreateCommandPool(
        device,
        VkCommandPoolCreateInfo(queueFamilyIndex = graphicsQueueFamilyIndex),
    )
    val commandBuffer = Vulkan.vkAllocateCommandBuffers(
        device,
        VkCommandBufferAllocateInfo(
            commandPool = commandPool,
            level = VkCommandBufferLevel.VK_COMMAND_BUFFER_LEVEL_PRIMARY,
            commandBufferCount = 1,
        ),
    )
    println("4. Command Buffer allocated: handle=$commandBuffer")

    // 6. Clean Up Resources (reverse order)
    Vulkan.vkDestroyCommandPool(device, commandPool)
    Vulkan.vkDestroyDevice(device)
    Vulkan.vkDestroyInstance(instance)
    println("5. Teardown complete")
}
```

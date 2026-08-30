# Vulkan Binding Annotations

The Vulkan Kotlin API intentionally uses readable Kotlin types such as `Long`, `Array<T>`,
and data classes. Vulkan's ABI contains information those types do not carry on their own:
the concrete handle type, whether an array is fixed or counted, union storage, and which
extension lookup path is required. These annotations preserve that information for the
native binding generators.

## Quick Guide

| Annotation | Apply to | Why it exists |
| --- | --- | --- |
| `VkHandleRef("VkDevice")` | Handle field or parameter | Restores the Vulkan handle type erased into Kotlin `Long`/`VkHandle`. |
| `VkReturnType("VkImage")` | Native function | Restores the Vulkan handle type of a `Long` return value. |
| `VkArray(sizeAlias = "count")` | Variable-length field | Connects a Vulkan pointer array with its sibling element-count field. |
| `VkConstArray("VK_MAX_...")` | Fixed-size field | Preserves a Vulkan compile-time array bound. |
| `VkUnionMember("color")` | Union type/member | Maps Kotlin's union-shaped model to a native union field. |
| `VkSingleton` | Extension function | Requests `vkGetInstanceProcAddr` lookup instead of direct linking. |
| `VkMutator` | Vulkan model class | Requests generated native-to-Kotlin object conversion code. |
| `JniNative("symbol")` | `actual external fun` | Keeps JNI glue generated while delegating backend behavior to `*_native.cpp`. |
| `VkPointer` | Field | Marks a single native pointer, including a pointer to one Vulkan struct. |
| `NativeSurfaceWindow` | Platform surface value | Marker reserved for platform surface binding code. |

## Examples

### Counted Array

```kotlin
@field:VkArray(sizeAlias = "attachmentCount")
val pAttachments: Array<VkPipelineColorBlendAttachmentState>? = null
```

The Kotlin model exposes a normal array and count field. The generator emits the native
pointer and count relationship expected by `VkPipelineColorBlendStateCreateInfo`.

### Handle Metadata

```kotlin
@VkReturnType("VkImage")
actual external fun vkGetSwapchainImagesKHR(...): LongArray

actual external fun vkDestroyImage(
    @VkHandleRef("VkDevice") device: Long,
    @VkHandleRef("VkImage") image: Long,
)
```

The annotation does not change the Kotlin value. It tells the native generator which Vulkan
handle typedef to cast or construct at the ABI boundary.

### Generated JNI Delegation

```kotlin
@JniNative("awake_vulkan_images_transition_image_layout")
actual external fun vkTransitionImageLayout(...)
```

The generated file owns the JNI symbol, parameter conversion, and error checks. The named
function is hand-authored in a platform native `*_native.cpp` file and owns Vulkan behavior.

## Ownership Rule

Annotations are binding metadata, not application behavior. Add one when the native ABI or
generator cannot derive the required information from the Kotlin declaration. Do not add an
annotation merely to document ordinary Kotlin semantics. `JniNative` is the current migration
path away from hand-authored bodies inside generated JNI files; unannotated legacy bindings
should be migrated one function at a time.

The annotation declarations and this documentation are authored by **Ron June Valdoz** under
Apache-2.0. **AwakeLab** is the project and publishing organization.

/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import kotlin.reflect.KClass

/** Marks a field as a single native pointer, including a pointer to one Vulkan struct. */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class VkPointer

/**
 * Describes a fixed-size C array represented by a Kotlin scalar/string field.
 *
 * @property arraySize The C array length, as the name of a size constant such as
 * `VK_MAX_PHYSICAL_DEVICE_NAME_SIZE`, or a literal count; empty when the generator infers it.
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class VkConstArray(val arraySize: String = "")

/**
 * Supplies the native Vulkan handle type erased by Kotlin's `Long` representation.
 *
 * @property name The native handle type, for example `VkDevice`.
 */
@Target(
    AnnotationTarget.FIELD,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.TYPE,
)
@Retention(AnnotationRetention.RUNTIME)
annotation class VkHandleRef(val name: String)

/**
 * Supplies the native Vulkan handle type erased by a Kotlin `Long` return value.
 *
 * @property name The native handle type, for example `VkImageView`.
 */
@Retention(AnnotationRetention.RUNTIME)
annotation class VkReturnType(val name: String)

/** Marks a platform surface/window value for the native binding layer. */
annotation class NativeSurfaceWindow

/**
 * Names the C-linkage implementation delegated to by the generated JNI wrapper.
 *
 * The implementation belongs in a platform native `*_native.cpp` source file; this
 * annotation carries generator metadata and does not create a Kotlin runtime dependency.
 *
 * @property symbol The C symbol the generated JNI wrapper calls, for example
 * `awake_vulkan_images_cmd_memory_barrier`.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class JniNative(val symbol: String)

/**
 * Marks an extension entry point the generator must resolve through `vkGetInstanceProcAddr`
 * rather than call directly, because the loader does not export it.
 *
 * ```
 * auto pfnDestroyDebugUtilsMessengerEXT =
 *         (PFN_vkDestroyDebugUtilsMessengerEXT) vkGetInstanceProcAddr(
 *                 instance, "vkDestroyDebugUtilsMessengerEXT");
 * ```
 *
 * Resolves an extension function through `vkGetInstanceProcAddr` instead of direct linking.
 */
annotation class VkSingleton

/**
 * Maps a Kotlin union member to its native parent field.
 *
 * @property alias The name of the native union field this member stands for.
 * @property saveToParent Whether the member's value is also written back to the parent field
 * when the struct is converted to its native form.
 */
annotation class VkUnionMember(val alias: String, val saveToParent: Boolean = false)

/**
 * Marks a field as a pointer-backed native array.
 *
 * This exists because Vulkan separates an array pointer from its element count, while Kotlin
 * stores them as ordinary properties. The generator uses [sizeAlias] to connect the two and
 * [stride] when the Vulkan count is expressed in bytes rather than elements.
 *
 * @property sizeAlias Name of the sibling count field. Empty means no count relationship.
 * @property stride Element type whose native size multiplies the count, e.g. `UInt::class`.
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class VkArray(
    val sizeAlias: String = "",
    val stride: KClass<*> = Nothing::class,
)

/** Marks a Vulkan model for generated native-to-Kotlin object conversion. */
@Target(AnnotationTarget.CLASS)
annotation class VkMutator

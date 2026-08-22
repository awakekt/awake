// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.vulkan

import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Deprecated("use VkArray instead")
annotation class VkPointer

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class VkConstArray(val arraySize: String = "")

@Target(
    AnnotationTarget.FIELD,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.TYPE,
)
@Retention(AnnotationRetention.RUNTIME)
annotation class VkHandleRef(val name: String)

@Retention(AnnotationRetention.RUNTIME)
annotation class VkReturnType(val name: String)

annotation class NativeSurfaceWindow

/**
 * Marks an extension entry point the generator must resolve through `vkGetInstanceProcAddr`
 * rather than call directly, because the loader does not export it.
 *
 * ```
 * auto pfnDestroyDebugUtilsMessengerEXT =
 *         (PFN_vkDestroyDebugUtilsMessengerEXT) vkGetInstanceProcAddr(
 *                 instance, "vkDestroyDebugUtilsMessengerEXT");
 * ```
 */
annotation class VkSingleton
annotation class VkUnionMember(val alias: String, val saveToParent: Boolean = false)

/**
 * Marks a field the generator emits as a sized native array.
 *
 * @property sizeAlias Name of the sibling count field. Empty means do not generate.
 * @property stride Element type whose native size multiplies the count, e.g. `UInt::class`
 * for `sizeof(uint32_t)`.
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class VkArray(
    val sizeAlias: String = "",
    val stride: KClass<*> = Nothing::class,
)

/**
 * Generates a `toObject` C++ method for this type.
 */
@Target(AnnotationTarget.CLASS)
annotation class VkMutator

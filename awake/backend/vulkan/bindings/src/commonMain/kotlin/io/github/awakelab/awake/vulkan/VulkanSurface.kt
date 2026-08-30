/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.vulkan

import io.github.awakelab.awake.vulkan.models.VkExtent2D

/**
 * Creates a `VkSurfaceKHR` for the given Vulkan [instance] and platform-native [window] handle
 * -- an `android.view.Surface` on Android, or a GLFW window handle (`Long`) on desktop. There's
 * no common supertype between the two (hence `Any`, cast internally by each actual).
 */
expect fun createSurface(instance: Long, window: Any): Long

/**
 * Returns the current drawable size, in framebuffer pixels, for platform surfaces whose
 * Vulkan capabilities report a variable extent.
 */
expect fun surfaceFramebufferExtent(window: Any): VkExtent2D?

/**
 * The window's size in logical/screen units, or `null` where the platform has no such distinct
 * unit (Android's `Surface` reports only physical pixels). Divided into
 * [surfaceFramebufferExtent] to recover the display's real pixel scale -- see
 * `GraphicsEngine.BackendResources.density`'s doc comment for why a UI layer needs that ratio
 * rather than either extent alone.
 */
expect fun windowLogicalExtent(window: Any): VkExtent2D?

/**
 * Tears down whatever platform-native window resources [window] represents, if any.
 * A no-op on Android, which owns its own `Surface`/window lifecycle; on desktop this
 * destroys the GLFW window and terminates GLFW.
 */
expect fun destroySurfaceWindow(window: Any)

/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.device

/** iOS sessions opt into disk persistence explicitly via `VulkanEngine.pipelineCacheStore`. */
internal actual fun defaultPlatformPipelineCacheStore(window: Any): VulkanPipelineCacheStore? = null

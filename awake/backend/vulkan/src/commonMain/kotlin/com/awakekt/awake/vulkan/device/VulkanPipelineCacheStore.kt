/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.device

/**
 * Persistence contract for Vulkan's device-wide `VkPipelineCache`.
 *
 * Implementations load previously serialized cache bytes before logical device creation and save
 * updated cache blobs returned by [GraphicsDevice.exportPipelineCacheData]. Implementations must
 * tolerate missing, empty, or corrupted storage by returning `null` from [load] and must swallow
 * storage I/O failures in [save] rather than throwing into the render loop.
 */
interface VulkanPipelineCacheStore {
    /**
     * Loads previously persisted raw `VkPipelineCache` bytes, or returns `null` when no valid cache
     * blob is available.
     */
    fun load(): ByteArray?

    /**
     * Persists raw `VkPipelineCache` [data] exported from a [GraphicsDevice].
     *
     * Empty payloads and payloads identical to the bytes already persisted should be ignored to
     * avoid unnecessary storage writes.
     */
    fun save(data: ByteArray)

    /** Shared defaults and sentinel implementations for [VulkanPipelineCacheStore]. */
    companion object {
        /** Default filename used by platform-managed pipeline cache stores. */
        const val DEFAULT_FILE_NAME: String = "awake-vk-pipeline-cache.bin"

        /** Default upper bound (64 MiB) for a persisted pipeline cache payload. */
        const val DEFAULT_MAX_BYTES: Int = 64 * 1024 * 1024

        /** A no-op store that disables loading and saving of pipeline cache bytes. */
        val None: VulkanPipelineCacheStore = object : VulkanPipelineCacheStore {
            override fun load(): ByteArray? = null

            override fun save(data: ByteArray) = Unit
        }
    }
}

/**
 * Resolves the platform's default [VulkanPipelineCacheStore] for a non-headless [window], or
 * returns `null` on platforms that do not enable automatic disk persistence by default.
 */
internal expect fun defaultPlatformPipelineCacheStore(window: Any): VulkanPipelineCacheStore?

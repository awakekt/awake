/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.device

import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * File-backed [VulkanPipelineCacheStore] that wraps raw `VkPipelineCache` bytes in a versioned
 * CRC-32 envelope and persists updates atomically via a sibling temporary file.
 *
 * Corrupted, truncated, oversized, or missing cache files are ignored cleanly (returning `null`
 * from [load]), and unchanged payloads skip disk writes to avoid redundant storage I/O.
 *
 * @property file Target file path where the enveloped pipeline cache is stored.
 * @param maxBytes Maximum allowed raw payload size in bytes. Defaults to
 * [VulkanPipelineCacheStore.DEFAULT_MAX_BYTES] (64 MiB).
 */
class FileVulkanPipelineCacheStore(
    val file: File,
    private val maxBytes: Int = VulkanPipelineCacheStore.DEFAULT_MAX_BYTES,
) : VulkanPipelineCacheStore {
    private val lock = Any()
    private var lastPersistedFingerprint: PipelineCacheFingerprint? = null

    init {
        require(maxBytes > 0) { "maxBytes must be positive, was $maxBytes." }
    }

    override fun load(): ByteArray? = synchronized(lock) {
        try {
            loadUnlocked()
        } catch (e: IOException) {
            pipelineCacheLogger.warn { "Failed to read Vulkan pipeline cache from ${file.path}: ${e.message}" }
            null
        } catch (e: SecurityException) {
            pipelineCacheLogger.warn { "Access denied reading Vulkan pipeline cache from ${file.path}: ${e.message}" }
            null
        } catch (e: InvalidPathException) {
            pipelineCacheLogger.warn { "Invalid Vulkan pipeline cache path ${file.path}: ${e.message}" }
            null
        }
    }

    override fun save(data: ByteArray) {
        synchronized(lock) {
            if (data.isEmpty() || !withinSizeCap(data)) return
            val fingerprint = VulkanPipelineCacheEnvelope.fingerprint(data)
            if (fingerprint == lastPersistedFingerprint && file.isFile) return
            persistEncoded(VulkanPipelineCacheEnvelope.encode(data), fingerprint)
        }
    }

    private fun withinSizeCap(data: ByteArray): Boolean {
        if (data.size <= maxBytes) return true
        pipelineCacheLogger.warn {
            "Skipping Vulkan pipeline cache save to ${file.path}: payload size ${data.size} exceeds cap $maxBytes"
        }
        return false
    }

    private fun loadUnlocked(): ByteArray? {
        val maxEnvelopeBytes = maxBytes.toLong() + VulkanPipelineCacheEnvelope.HEADER_SIZE.toLong()
        val length = if (file.isFile) file.length() else 0L
        if (length !in 1L..maxEnvelopeBytes) {
            if (length > maxEnvelopeBytes) {
                pipelineCacheLogger.warn {
                    "Ignoring oversized Vulkan pipeline cache at ${file.path} ($length bytes > $maxEnvelopeBytes limit)"
                }
            }
            return null
        }
        val decoded = VulkanPipelineCacheEnvelope.decode(file.readBytes(), maxBytes)
        if (decoded == null) {
            pipelineCacheLogger.warn { "Ignoring corrupted or incompatible Vulkan pipeline cache envelope at ${file.path}" }
        } else {
            lastPersistedFingerprint = VulkanPipelineCacheEnvelope.fingerprint(decoded)
        }
        return decoded
    }

    private fun persistEncoded(encoded: ByteArray, fingerprint: PipelineCacheFingerprint) {
        var tempPath: Path? = null
        try {
            val target = file.absoluteFile
            val parent = target.parentFile
            if (parent != null && !ensureDirectory(parent)) {
                pipelineCacheLogger.warn { "Could not create parent directory for Vulkan pipeline cache: ${parent.path}" }
                return
            }
            if (target.isDirectory) {
                pipelineCacheLogger.warn { "Cannot save Vulkan pipeline cache: ${target.path} is a directory" }
                return
            }
            val parentPath = parent?.toPath() ?: target.toPath().parent
            val createdTemp = Files.createTempFile(parentPath, "${target.name}.", ".tmp")
            tempPath = createdTemp
            Files.write(createdTemp, encoded)
            moveIntoPlace(createdTemp, target.toPath())
            tempPath = null
            lastPersistedFingerprint = fingerprint
        } catch (e: IOException) {
            pipelineCacheLogger.warn { "Failed to persist Vulkan pipeline cache to ${file.path}: ${e.message}" }
        } catch (e: SecurityException) {
            pipelineCacheLogger.warn { "Access denied persisting Vulkan pipeline cache to ${file.path}: ${e.message}" }
        } catch (e: InvalidPathException) {
            pipelineCacheLogger.warn { "Invalid Vulkan pipeline cache path ${file.path}: ${e.message}" }
        } finally {
            tempPath?.let(::deleteTempQuietly)
        }
    }

    private fun ensureDirectory(dir: File): Boolean = dir.isDirectory || dir.mkdirs() || dir.isDirectory

    private fun moveIntoPlace(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun deleteTempQuietly(path: Path) {
        try {
            Files.deleteIfExists(path)
        } catch (_: IOException) {
            // Best-effort cleanup of abandoned temporary file.
        } catch (_: SecurityException) {
            // Best-effort cleanup of abandoned temporary file.
        }
    }
}

/** Desktop sessions opt into disk persistence explicitly via `VulkanEngine.pipelineCacheStore`. */
internal actual fun defaultPlatformPipelineCacheStore(window: Any): VulkanPipelineCacheStore? = null

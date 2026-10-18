/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.cli

import com.awakekt.awake.project.AwakeAssetLockEntry
import com.awakekt.awake.project.AwakeProjectValidator
import com.awakekt.awake.project.ProjectContentValidator
import com.awakekt.awake.project.ProjectIssueCode
import com.awakekt.awake.project.runtime.PROJECT_ASSETS_LOCK
import java.io.File
import java.security.MessageDigest

/**
 * Checks a project's [PROJECT_ASSETS_LOCK], when it has one: that it is a valid lock, and that each
 * asset it pins is there, with the pinned size and SHA-256. A project with no lock has nothing to check.
 */
internal class AssetLockCheck(private val root: File) {
    fun findings(): List<Finding> {
        val file = root.resolve(PROJECT_ASSETS_LOCK)
        return if (file.isFile) lockFindings(file.readText()) else emptyList()
    }

    private fun lockFindings(text: String): List<Finding> {
        val lock = try {
            AwakeProjectValidator.decodeAssetsLock(text)
        } catch (invalid: IllegalArgumentException) {
            // What the decoder throws for text that isn't a lock, its SerializationException included.
            return listOf(lockFinding("can't be read as an asset lock: ${invalid.message}", null, ProjectIssueCode.ASSET_LOCK_INVALID))
        }
        val shape = ProjectContentValidator.assetsLockIssueDetails(lock).map { it.toFinding(PROJECT_ASSETS_LOCK) }
        return shape + lock.assets.mapNotNull { (path, pin) -> pinFinding(path, pin) }
    }

    /** What is wrong with the asset at [path] against its [pin], or null when it matches. An unsafe path is in the lock's own findings. */
    private fun pinFinding(path: String, pin: AwakeAssetLockEntry): Finding? {
        val asset = root.resolve(path)
        return when {
            !AwakeProjectValidator.isSafeProjectPath(path) -> null
            !asset.isFile -> lockFinding("pins $path, which is missing", path, ProjectIssueCode.ASSET_REFERENCE_MISSING)
            pin.sizeBytes != null && pin.sizeBytes != asset.length() ->
                lockFinding("$path is ${asset.length()} bytes; the lock pins ${pin.sizeBytes}", path, ProjectIssueCode.ASSET_HASH_MISMATCH)
            else -> sha256(asset).takeIf { it != pin.sha256.lowercase() }?.let { digest ->
                lockFinding("$path has SHA-256 $digest; the lock pins ${pin.sha256}", path, ProjectIssueCode.ASSET_HASH_MISMATCH)
            }
        }
    }

    private fun lockFinding(message: String, path: String?, code: ProjectIssueCode) =
        Finding(Severity.Error, message, PROJECT_ASSETS_LOCK, path, code.value)

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val BUFFER_BYTES = 64 * 1024
    }
}

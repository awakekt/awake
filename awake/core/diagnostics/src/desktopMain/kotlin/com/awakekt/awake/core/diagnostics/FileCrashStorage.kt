/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.diagnostics

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * File-backed [CrashStorage] for JVM desktop and Android hosts that persists each key as a
 * collision-safe UTF-8 file directly inside [directory].
 *
 * Every key is mapped to a deterministic filename combining a sanitized lowercase prefix with a
 * SHA-256 digest of the raw key, preventing path traversal (`..`, `/`, `\`), Windows reserved
 * device names, case-insensitive filesystem collisions, and long-key path overflows.
 *
 * Writes go through a sibling temporary file and atomic rename (falling back to a standard replace
 * move when the host filesystem does not support atomic moves) so an abrupt process crash never
 * leaves a half-written payload. All I/O errors are caught so crash recording never interrupts the
 * host application.
 *
 * @property directory Root directory under which crash-storage key files are persisted.
 */
class FileCrashStorage(val directory: File) : CrashStorage {
    /**
     * Creates a [FileCrashStorage] backed by [directory].
     *
     * @param directory Root [Path] under which crash-storage key files are persisted.
     */
    constructor(directory: Path) : this(directory.toFile())

    private val dirPath: Path = directory.toPath().toAbsolutePath().normalize()
    private val lock = Any()

    override fun read(key: String): String? = synchronized(lock) {
        runCatching {
            val target = fileForKey(key)
            if (!Files.isRegularFile(target)) {
                null
            } else {
                String(Files.readAllBytes(target), Charsets.UTF_8)
            }
        }.getOrNull()
    }

    override fun write(key: String, value: String) {
        synchronized(lock) {
            runCatching {
                Files.createDirectories(dirPath)
                val target = fileForKey(key)
                val tmp = Files.createTempFile(dirPath, TMP_PREFIX, TMP_SUFFIX)
                var moved = false
                try {
                    Files.write(tmp, value.toByteArray(Charsets.UTF_8))
                    moveIntoPlace(tmp, target)
                    moved = true
                } finally {
                    if (!moved) {
                        runCatching {
                            Files.deleteIfExists(tmp)
                            Unit
                        }.getOrDefault(Unit)
                    }
                }
            }.getOrDefault(Unit)
        }
    }

    override fun remove(key: String) {
        synchronized(lock) {
            runCatching {
                Files.deleteIfExists(fileForKey(key))
                Unit
            }.getOrDefault(Unit)
        }
    }

    internal fun fileForKey(key: String): Path {
        val hash = sha256Hex(key)
        val slug = sanitizeSlug(key)
        val fileName = if (slug.isEmpty()) {
            "$FILE_PREFIX$hash$FILE_SUFFIX"
        } else {
            "$FILE_PREFIX$slug-$hash$FILE_SUFFIX"
        }
        val resolved = dirPath.resolve(fileName).normalize()
        require(resolved.parent == dirPath) {
            "Resolved crash storage path escaped storage directory."
        }
        return resolved
    }

    private fun moveIntoPlace(source: Path, target: Path) {
        try {
            Files.move(
                source,
                target,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private companion object {
        private const val FILE_PREFIX = "crash-"
        private const val FILE_SUFFIX = ".txt"
        private const val TMP_PREFIX = ".crash-"
        private const val TMP_SUFFIX = ".tmp"
        private const val MAX_SLUG_LENGTH = 48
        private const val HEX_CHARS = "0123456789abcdef"

        fun sanitizeSlug(key: String): String {
            val out = StringBuilder(minOf(key.length, MAX_SLUG_LENGTH))
            var lastWasUnderscore = false
            for (ch in key) {
                if (out.length >= MAX_SLUG_LENGTH) break
                val mapped = mapSlugChar(ch)
                if (mapped != '_') {
                    out.append(mapped)
                    lastWasUnderscore = false
                } else if (out.isNotEmpty() && !lastWasUnderscore) {
                    out.append('_')
                    lastWasUnderscore = true
                }
            }
            return out.toString().trimEnd('_', '-')
        }

        private fun mapSlugChar(ch: Char): Char = when {
            ch in 'a'..'z' || ch in '0'..'9' || ch == '-' -> ch
            ch in 'A'..'Z' -> ch.lowercaseChar()
            else -> '_'
        }

        fun sha256Hex(key: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
            val chars = CharArray(digest.size * 2)
            for (i in digest.indices) {
                val b = digest[i].toInt() and 0xFF
                chars[i * 2] = HEX_CHARS[b ushr 4]
                chars[i * 2 + 1] = HEX_CHARS[b and 0x0F]
            }
            return chars.concatToString()
        }
    }
}

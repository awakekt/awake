/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.settings

import com.awakekt.awake.core.io.FilePath
import com.awakekt.awake.core.io.FileSystem
import com.awakekt.awake.core.io.FileSystemError
import com.awakekt.awake.core.io.FileSystemException

/**
 * Where [Settings] keep the saved values: all of them, as one text, read once and written whole. The schema
 * and the store never see the backend, so another one (browser `localStorage`, a platform preferences store)
 * is one small class. [FileSettingsStorage] is the engine's default.
 */
interface SettingsStorage {
    /** The saved text, or null when nothing was saved yet. A failure to read is thrown. */
    suspend fun read(): String?

    /** Replaces the saved text with [text]. A failure to write is thrown. */
    suspend fun write(text: String)

    /** Keeps [text], which could not be read as settings, somewhere it is not read again, for a person to look at. */
    suspend fun keepAside(text: String)
}

/**
 * [SettingsStorage] in one file of a [FileSystem]: `createPlatformFileSystem(root)` keeps it in browser storage
 * (IndexedDB) on the web and in a file under `root` on desktop and Android. Unreadable text is kept next to it,
 * under the same name with `.unreadable` added.
 *
 * @property files the file system the file is in.
 * @property path the file.
 */
class FileSettingsStorage(
    val files: FileSystem,
    val path: FilePath = FilePath.of("settings.json"),
) : SettingsStorage {
    override suspend fun read(): String? {
        val bytes = files.read(path).getOrElse { return if (it.isNotFound()) null else throw it }
        return bytes.decodeToString()
    }

    override suspend fun write(text: String) {
        files.write(path, text.encodeToByteArray()).getOrThrow()
    }

    override suspend fun keepAside(text: String) {
        files.write(FilePath.of(path.value + ".unreadable"), text.encodeToByteArray()).getOrThrow()
    }

    private fun Throwable.isNotFound(): Boolean = this is FileSystemException && error is FileSystemError.NotFound
}

/** [SettingsStorage] in memory, for tests and for a game that keeps nothing between sessions. */
class MemorySettingsStorage(
    /** The saved text, or null. */
    var text: String? = null,
) : SettingsStorage {
    /** What [keepAside] was given, or null. */
    var keptAside: String? = null
        private set

    override suspend fun read(): String? = text

    override suspend fun write(text: String) {
        this.text = text
    }

    override suspend fun keepAside(text: String) {
        keptAside = text
    }
}

/** Something [Settings] could not use as stored or as given. Nothing in settings throws; the app logs these. */
sealed class SettingsProblem {
    /** One line saying what happened, for a log. */
    abstract val message: String

    override fun toString(): String = message

    /**
     * The saved text was not settings; it was kept aside and every setting starts from its default.
     *
     * @property reason why it could not be read.
     */
    data class Unreadable(val reason: String) : SettingsProblem() {
        override val message: String get() = "saved settings could not be read ($reason); kept aside, using defaults"
    }

    /**
     * A saved or given value of [key] that was refused for [reason]; the setting keeps what it had.
     *
     * @property key the setting.
     * @property given the text of the value.
     * @property reason why it was refused.
     */
    class Invalid(val key: SettingKey<*>, val given: String, val reason: String) : SettingsProblem() {
        override val message: String get() = "setting '${key.name}' '$given' $reason; not used"
    }

    /**
     * Reading or writing the storage failed; the values in memory are unchanged and the next change tries again.
     *
     * @property cause the failure.
     */
    class StorageFailed(val cause: Throwable) : SettingsProblem() {
        override val message: String get() = "settings storage failed: ${cause.message ?: cause::class.simpleName}"
    }
}

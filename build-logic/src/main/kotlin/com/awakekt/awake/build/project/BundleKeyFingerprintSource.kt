/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.project

import java.io.File
import java.security.MessageDigest
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

/**
 * A labelled fingerprint of the bundle key(s) `generateContentBundles` seals with, read from key files
 * or environment variables, for [GenerateContentBundlesTask.keyFingerprint]. Gradle runs a value
 * source again on every build and keeps only what it answers, so a new key reruns the task, also from
 * the configuration cache, and neither the cache nor the task history ever holds a key.
 *
 * The fingerprint hashes `"awake-bundle-key-fingerprint" 0x00` and each configured key source without
 * exposing a plain hash of any key. With no key source it answers nothing; a missing file or unset
 * variable mixes in a missing marker, and the worker then fails naming it.
 */
abstract class BundleKeyFingerprintSource : ValueSource<String, BundleKeyFingerprintSource.Parameters> {
    /** Where the default key and any per-group keys are, as `projectContent` names them. */
    interface Parameters : ValueSourceParameters {
        val keyFile: RegularFileProperty
        val environmentVariable: Property<String>
        val groupKeyFiles: MapProperty<String, File>
        val groupEnvironmentVariables: MapProperty<String, String>
    }

    override fun obtain(): String? {
        val file = parameters.keyFile.orNull?.asFile
        val variable = parameters.environmentVariable.orNull
        val groupFiles = parameters.groupKeyFiles.getOrElse(emptyMap())
        val groupVariables = parameters.groupEnvironmentVariables.getOrElse(emptyMap())
        if (file == null && variable == null && groupFiles.isEmpty() && groupVariables.isEmpty()) return null

        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(LABEL)
        if (file != null || variable != null) {
            updateSource(digest, "", file, variable)
        }
        (groupFiles.keys + groupVariables.keys).sorted().forEach { group ->
            updateSource(digest, group, groupFiles[group], groupVariables[group])
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun updateSource(digest: MessageDigest, group: String, file: File?, variable: String?) {
        digest.update(group.encodeToByteArray())
        digest.update(0)
        val key = when {
            file != null -> file.takeIf { it.isFile }?.readBytes()
            variable != null -> System.getenv(variable)?.trim()?.encodeToByteArray()
            else -> null
        }
        if (key == null) {
            digest.update(MISSING)
            return
        }
        digest.update(PRESENT)
        digest.update(key)
        key.fill(0)
    }

    private companion object {
        val LABEL = "awake-bundle-key-fingerprint".encodeToByteArray() + byteArrayOf(0)
        const val MISSING: Byte = 0
        const val PRESENT: Byte = 1
    }
}

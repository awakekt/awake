/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.project

import java.security.MessageDigest
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

/**
 * A labelled fingerprint of the bundle key `generateContentBundles` seals with, read from the key file
 * or the environment variable, for [GenerateContentBundlesTask.keyFingerprint]. Gradle runs a value
 * source again on every build and keeps only what it answers, so a new key reruns the task, also from
 * the configuration cache, and neither the cache nor the task history ever holds the key.
 *
 * The fingerprint is `SHA-256("awake-bundle-key-fingerprint" 0x00 key)`, so it can't be matched
 * against a plain hash of the key. With no key source it answers nothing; a missing file or unset
 * variable answers a marker, and the worker then fails naming it.
 */
abstract class BundleKeyFingerprintSource : ValueSource<String, BundleKeyFingerprintSource.Parameters> {
    /** Where the key is: one of the two, as `projectContent` names them. */
    interface Parameters : ValueSourceParameters {
        val keyFile: RegularFileProperty
        val environmentVariable: Property<String>
    }

    override fun obtain(): String? {
        val file = parameters.keyFile.orNull?.asFile
        val variable = parameters.environmentVariable.orNull
        val key = when {
            file != null -> file.takeIf { it.isFile }?.readBytes() ?: return "missing key file"
            variable != null -> System.getenv(variable)?.trim()?.encodeToByteArray() ?: return "unset key variable"
            else -> return null
        }
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(LABEL)
        val fingerprint = digest.digest(key)
        key.fill(0)
        return fingerprint.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private companion object {
        val LABEL = "awake-bundle-key-fingerprint".encodeToByteArray() + byteArrayOf(0)
    }
}

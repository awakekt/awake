/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:JvmName("PluginArchiveSignerMain")

package com.awakekt.awake.pluginarchive

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.system.exitProcess

/** The environment variable [main] reads the publisher's private key from, keeping it out of the process list. */
public const val PLUGIN_SIGNING_KEY_ENVIRONMENT_VARIABLE: String = "AWAKE_PLUGIN_SIGNING_KEY"

/**
 * [archive] rewritten with a `signature.sig` entry under [privateKey], replacing any it had: a
 * blocking desktop JVM wrapper around [PluginArchiveSigner.signedEntries] for packaging tools. The
 * archive is read with [PluginArchiveReader]'s checks first, so an archive a host would refuse is
 * never signed. Entries are written sorted by name, deflated, signature last.
 *
 * @throws PluginArchiveException when [archive] fails those checks.
 */
public fun PluginArchiveSigner.sign(archive: ByteArray, privateKey: String): ByteArray {
    val signed = runWithoutSuspending { signedEntries(PluginArchiveReader.read(archive).entries, privateKey) }
    val (signature, entries) = signed.partition { it.name == PLUGIN_ARCHIVE_SIGNATURE_ENTRY_NAME }
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        (entries.sortedBy { it.name } + signature).forEach { entry ->
            zip.putNextEntry(ZipEntry(entry.name))
            zip.write(entry.content)
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}

/**
 * Signs the archive at `args[0]` in place with the key in [PLUGIN_SIGNING_KEY_ENVIRONMENT_VARIABLE].
 * A build runs it in its own JVM, so the library's Kotlin and crypto never meet the build tool's:
 * `java -cp <plugin-archive-desktop and its dependencies> com.awakekt.awake.pluginarchive.PluginArchiveSignerMain <archive>`.
 */
public fun main(args: Array<String>) {
    val key = System.getenv(PLUGIN_SIGNING_KEY_ENVIRONMENT_VARIABLE)
    if (args.size != 1 || key.isNullOrBlank()) {
        System.err.println("Usage: $PLUGIN_SIGNING_KEY_ENVIRONMENT_VARIABLE=<private key> PluginArchiveSignerMain <archive.awakeplugin>")
        exitProcess(2)
    }
    val file = File(args[0])
    file.writeBytes(PluginArchiveSigner.sign(file.readBytes(), key))
}

/**
 * Runs [block] to completion on this thread. Only for suspend code that never actually suspends on
 * the JVM (the JDK provider and `java.util.zip` are synchronous), never for code that waits.
 */
private fun <T> runWithoutSuspending(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
    return checkNotNull(outcome) { "The block suspended; it cannot run without a coroutine." }.getOrThrow()
}

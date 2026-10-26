/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.pluginarchive

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.RSA
import dev.whyoleg.cryptography.algorithms.SHA256
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.system.exitProcess

/**
 * Signs `.awakeplugin` archives on the JVM, for packaging tools such as `templates/plugin`'s
 * `awakePlugin` task: the publisher-side half of [PluginArchiveVerifier], so the signing path and
 * the checking path share one digest and payload instead of a mirrored copy.
 *
 * The signature is RSASSA-PKCS1-v1_5 / SHA-256 (`SHA256withRSA`) over
 * [PluginArchiveDigest.signingPayload] of the archive's canonical digest, made with the publisher's
 * RSA private key as PKCS#8, PEM or bare base64 DER (what `openssl genpkey` writes). Desktop JVM
 * only, and blocking: the JDK's provider is synchronous, so nothing here suspends.
 *
 * A build can run it in its own JVM, so its Kotlin and crypto libraries never meet the build
 * tool's: `java -cp <plugin-archive-desktop and its dependencies> com.awakekt.awake.pluginarchive.PluginArchiveSigner
 * <archive>` signs the file in place with the key in the `AWAKE_PLUGIN_SIGNING_KEY` environment
 * variable, which keeps the key out of the process list.
 */
public object PluginArchiveSigner {
    /** The environment variable [main] reads the private key from. */
    public const val SIGNING_KEY_ENVIRONMENT_VARIABLE: String = "AWAKE_PLUGIN_SIGNING_KEY"

    /** The `signature.sig` contents for [entries] (any existing signature entry is ignored) under [privateKey]. */
    public fun signature(entries: List<PluginArchiveEntry>, privateKey: String): ByteArray {
        val digest = CryptographyProvider.Default.get(SHA256).hasher()
            .hashBlocking(PluginArchiveDigest.canonicalBytes(entries))
        return CryptographyProvider.Default.get(RSA.PKCS1)
            .privateKeyDecoder(SHA256)
            .decodeFromByteArrayBlocking(RSA.PrivateKey.Format.DER, pemOrBase64Der(privateKey))
            .signatureGenerator()
            .generateSignatureBlocking(PluginArchiveDigest.signingPayload(digest))
    }

    /**
     * [archive] rewritten with a `signature.sig` entry under [privateKey], replacing any it had.
     * The archive is read with [PluginArchiveReader]'s checks first, so an archive a host would
     * refuse is never signed. Entries are written sorted by name, deflated, signature last.
     *
     * @throws PluginArchiveException when [archive] fails those checks.
     */
    public fun sign(archive: ByteArray, privateKey: String): ByteArray {
        val entries = runWithoutSuspending { PluginArchiveReader.read(archive) }.entries
            .filterNot { it.name == PLUGIN_ARCHIVE_SIGNATURE_ENTRY_NAME }
        val signature = PluginArchiveEntry(PLUGIN_ARCHIVE_SIGNATURE_ENTRY_NAME, signature(entries, privateKey))
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

    /** Signs the archive at `args[0]` in place with the key in [SIGNING_KEY_ENVIRONMENT_VARIABLE]. */
    @JvmStatic
    public fun main(args: Array<String>) {
        val key = System.getenv(SIGNING_KEY_ENVIRONMENT_VARIABLE)
        if (args.size != 1 || key.isNullOrBlank()) {
            System.err.println("Usage: $SIGNING_KEY_ENVIRONMENT_VARIABLE=<private key> PluginArchiveSigner <archive.awakeplugin>")
            exitProcess(2)
        }
        val file = File(args[0])
        file.writeBytes(sign(file.readBytes(), key))
    }
}

/**
 * Runs [block] to completion on this thread. Only for suspend code that never actually suspends on
 * the JVM ([PluginArchiveReader] inflates with `java.util.zip`), never for code that waits.
 */
private fun <T> runWithoutSuspending(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
    return checkNotNull(outcome) { "The block suspended; it cannot run without a coroutine." }.getOrThrow()
}

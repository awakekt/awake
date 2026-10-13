import java.io.File
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

group = "com.awakekt.awake.plugin.template"
version = "0.1.0"

kotlin {
    jvmToolchain(17)
    jvm("desktop")
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            // The contract is the only editor API a plugin may use. The host supplies it at run time,
            // so the archive below does not bundle it.
            implementation(libs.awake.editor.contract)
            implementation(libs.awake.ui.shadcn)
            implementation(libs.awake.scene.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// Reserved entry name for the publisher's signature; excluded from the digest it covers. Must
// match com.awakekt.awake.editor.core.plugin.PLUGIN_ARCHIVE_SIGNATURE_ENTRY_NAME.
val pluginSignatureEntryName = "signature.sig"

// A .awakeplugin is a zip: plugin.json at the root and the plugin's classes under lib/.
// Desktop hosts load it at run time; other platforms compile the plugin module in instead.
val awakePlugin by tasks.registering(Zip::class) {
    group = "awake"
    description = "Packages the desktop plugin as a .awakeplugin archive, signed when a " +
        "publisher key is available."
    archiveFileName = "${project.group}-${project.version}.awakeplugin"
    destinationDirectory = layout.buildDirectory.dir("awakeplugin")
    from(layout.projectDirectory.file("plugin.json")) {
        expand("version" to project.version)
    }
    from(tasks.named("desktopJar")) { into("lib") }

    // Never read from the repository: the signing key is a secret, supplied by the environment
    // a publisher's own machine or CI controls, or (for local testing) a Gradle property.
    val signingKey = providers.environmentVariable("AWAKE_PLUGIN_SIGNING_KEY")
        .orElse(providers.gradleProperty("awake.plugin.signingKey"))
    inputs.property("awakePluginIsSigned", signingKey.isPresent)

    doLast {
        val archive = archiveFile.get().asFile
        val key = signingKey.orNull
        if (key.isNullOrBlank()) {
            logger.lifecycle(
                "awakePlugin: no AWAKE_PLUGIN_SIGNING_KEY (or -Pawake.plugin.signingKey) is set; " +
                    "packaged ${archive.name} unsigned.",
            )
            return@doLast
        }
        signPluginArchive(archive, key, pluginSignatureEntryName)
        logger.lifecycle("awakePlugin: signed ${archive.name}.")
    }
}

/**
 * Rewrites [archive] with a `signature.sig` entry covering every other entry's canonical digest,
 * signed with the publisher's RSA private key (PEM or base64 PKCS#8). Mirrors
 * `com.awakekt.awake.editor.core.plugin.PluginArchiveDigest` and `PluginArchiveVerifier`: this
 * module has no dependency on that published artifact, so the algorithm is kept in sync by the
 * pinned test vectors both sides share rather than shared code.
 */
fun signPluginArchive(archive: File, pemOrBase64PrivateKey: String, signatureEntryName: String) {
    val entries = ZipFile(archive).use { zip ->
        zip.entries().asSequence()
            .filterNot { it.isDirectory || it.name == signatureEntryName }
            .map { entry -> entry.name to zip.getInputStream(entry).use { it.readBytes() } }
            .toList()
    }

    val digest = canonicalDigest(entries)
    val payload = ("awake-plugin-archive-v1\n" + digest.toHexString()).toByteArray(Charsets.UTF_8)
    val signature = signWithRsaPrivateKey(payload, pemOrBase64PrivateKey)

    val tempFile = File.createTempFile(archive.name, ".tmp", archive.parentFile)
    ZipOutputStream(tempFile.outputStream()).use { out ->
        entries.sortedBy { it.first }.forEach { (name, bytes) ->
            out.putNextEntry(ZipEntry(name))
            out.write(bytes)
            out.closeEntry()
        }
        out.putNextEntry(ZipEntry(signatureEntryName))
        out.write(signature)
        out.closeEntry()
    }
    check(archive.delete()) { "Could not replace ${archive.path} with its signed copy." }
    check(tempFile.renameTo(archive)) { "Could not move the signed archive into place at ${archive.path}." }
}

/**
 * Entries sorted by name, each framed by a 4-byte big-endian length before its name and before its
 * content, hashed with SHA-256. Deterministic regardless of zip entry order.
 */
fun canonicalDigest(entries: List<Pair<String, ByteArray>>): ByteArray {
    val digest = MessageDigest.getInstance("SHA-256")
    entries.sortedBy { it.first }.forEach { (name, content) ->
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        digest.update(nameBytes.size.toBigEndianBytes())
        digest.update(nameBytes)
        digest.update(content.size.toBigEndianBytes())
        digest.update(content)
    }
    return digest.digest()
}

fun signWithRsaPrivateKey(payload: ByteArray, pemOrBase64Key: String): ByteArray {
    val body = pemOrBase64Key.lineSequence()
        .filterNot { it.isBlank() || it.trimStart().startsWith("-----") }
        .joinToString("") { it.trim() }
    val keyBytes = Base64.getDecoder().decode(body)
    val privateKey = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyBytes))
    return Signature.getInstance("SHA256withRSA").run {
        initSign(privateKey)
        update(payload)
        sign()
    }
}

fun Int.toBigEndianBytes(): ByteArray =
    byteArrayOf((this ushr 24).toByte(), (this ushr 16).toByte(), (this ushr 8).toByte(), toByte())

fun ByteArray.toHexString(): String = joinToString("") {
    val hex = (it.toInt() and 0xFF).toString(16)
    if (hex.length == 1) "0$hex" else hex
}

/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.netdemo

import io.ktor.network.tls.certificates.buildKeyStore
import io.ktor.network.tls.certificates.saveToFile
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlin.io.encoding.Base64

/**
 * A self-signed keystore for running the demo over `wss://` locally.
 *
 * **This is development scaffolding, not a security model.** A self-signed certificate proves
 * that traffic is encrypted, and nothing whatsoever about who is on the other end -- there is
 * no authority vouching for the identity. A real deployment gets a CA-issued certificate; the
 * plan's Phase 1 requirement for WSS means that one, not this one.
 *
 * The keystore and its password live under `build/`, are generated on first use, and are
 * regenerated whenever they go missing. The password is random per machine rather than a
 * constant in source, so there is no credential in this repository to leak or to be copied
 * into something real by accident.
 */
object DevTls {
    const val ALIAS: String = "net-demo-dev"

    private const val DIRECTORY = "build/dev-tls"
    private const val KEYSTORE_NAME = "keystore.jks"
    private const val PASSWORD_NAME = "keystore.password"
    private const val PASSWORD_BYTES = 24

    val keyStoreFile: File get() = File(DIRECTORY, KEYSTORE_NAME)

    /** Generates the keystore on first call, then reuses it. */
    fun keyStore(): KeyStore {
        val password = password()
        if (!keyStoreFile.exists()) {
            buildKeyStore {
                certificate(ALIAS) {
                    this.password = password
                    // Must cover every host the demo is reached by, or the handshake fails on
                    // name mismatch rather than on trust.
                    domains = listOf("127.0.0.1", "0.0.0.0", "localhost")
                }
            }.saveToFile(keyStoreFile, password)
        }
        return KeyStore.getInstance("JKS").apply {
            keyStoreFile.inputStream().use { load(it, password.toCharArray()) }
        }
    }

    fun passwordChars(): CharArray = password().toCharArray()

    /**
     * Trust anchored on the generated certificate specifically -- **not** a trust-all manager.
     * Trusting everything would make the client accept any certificate at all, which removes
     * the one property TLS is here to provide and is a habit that escapes into production.
     */
    fun trustManager(): X509TrustManager {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(keyStore())
        return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    private fun password(): String {
        val file = File(DIRECTORY, PASSWORD_NAME)
        if (!file.exists()) {
            file.parentFile.mkdirs()
            val random = ByteArray(PASSWORD_BYTES).also { SecureRandom().nextBytes(it) }
            file.writeText(Base64.UrlSafe.encode(random))
        }
        return file.readText().trim()
    }
}

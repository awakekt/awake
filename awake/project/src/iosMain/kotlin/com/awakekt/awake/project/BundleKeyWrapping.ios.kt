/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

/**
 * Ephemeral RSA-2048-OAEP-SHA256 key exchange backed by Apple Security (`SecRsaOaep`,
 * `kSecKeyAlgorithmRSAEncryptionOAEPSHA256`, empty OAEP label).
 */
internal actual suspend fun createPlatformSessionKeyExchange(): BundleSessionKeyExchange =
    createDefaultProviderSessionKeyExchange()

/**
 * Null by default on iOS: Core never writes raw software keys to disk (D42), and Keychain access
 * groups and protection classes belong to an app-owned [BundleKeyStore].
 */
internal actual fun createPlatformBundleKeyStore(namespace: String): BundleKeyStore? = null

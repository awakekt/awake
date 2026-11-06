/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

/**
 * Ephemeral RSA-2048-OAEP-SHA256 key exchange backed by Android's Conscrypt JCA provider.
 *
 * `AndroidKeyStore`'s RSA cipher hardcodes OAEP MGF1 to SHA-1 prior to API 35 (`minSdk` is 33),
 * which is incompatible with standard RFC 8017 RSA-OAEP-SHA256 (`MGF1-SHA256`) used by WebCrypto,
 * JCA `OAEPParameterSpec`, and Apple `SecRsaOaep`, so ephemeral session unwrapping uses JCA in
 * memory and zeroes intermediate key buffers on [BundleKeyHandle.close].
 */
internal actual suspend fun createPlatformSessionKeyExchange(): BundleSessionKeyExchange =
    createDefaultProviderSessionKeyExchange()

/**
 * Null by default on Android: Core never writes raw software keys to disk (D42), and hardware-backed
 * persistence (`AndroidKeyStore` AES-GCM wrapping) belongs to an app-owned [BundleKeyStore].
 */
internal actual fun createPlatformBundleKeyStore(namespace: String): BundleKeyStore? = null

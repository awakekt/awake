/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

/** Ephemeral RSA-2048-OAEP-SHA256 key exchange backed by the JDK JCA provider. */
internal actual suspend fun createPlatformSessionKeyExchange(): BundleSessionKeyExchange =
    createDefaultProviderSessionKeyExchange()

/**
 * Null on desktop: Core never writes raw software keys to disk by default (D42); a desktop app that
 * persists keys offline supplies its own [BundleKeyStore] backed by an OS credential store.
 */
internal actual fun createPlatformBundleKeyStore(namespace: String): BundleKeyStore? = null

/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

/**
 * Pluggable persistence for [BundleKeyHandle]s across runs of an app, paired with
 * [BundleKeyProvider.wrapped].
 *
 * On Web (`wasmJs`), [platformBundleKeyStore] returns an `IndexedDbBundleKeyStore` that persists
 * non-extractable WebCrypto `CryptoKey` objects through HTML structured clone in IndexedDB while
 * keeping `extractable == false`. On native and desktop targets, an app may supply a store backed by
 * its own hardware-protected key container (Android Keystore, iOS Keychain, or an OS credential
 * store).
 *
 * When a persisted key fails authentication on a whole bundle that matches the [ProjectIndex],
 * [IndexedAssetSource] evicts that `keyId` via [delete] so the next read fetches a fresh wrapped key.
 */
interface BundleKeyStore {
    /**
     * Reads the persisted handle for [keyId], or null when none is stored or storage is unavailable.
     *
     * @param keyId Which master key to look up.
     * @return An open [BundleKeyHandle] for [keyId], or null on a miss.
     */
    suspend fun get(keyId: String): BundleKeyHandle?

    /**
     * Persists [handle] under its [BundleKeyHandle.keyId], replacing any earlier entry for that id.
     *
     * @param handle The active key handle to store.
     */
    suspend fun put(handle: BundleKeyHandle)

    /**
     * Removes any persisted handle for [keyId].
     *
     * @param keyId Which master key to remove.
     */
    suspend fun delete(keyId: String)
}

/**
 * The platform's zero-context non-extractable [BundleKeyStore] for [BundleKeyProvider.wrapped], or
 * null where the platform has none without app-owned credentials or a `Context`:
 *
 * | Platform | Where | Value |
 * | --- | --- | --- |
 * | Web (`wasmJs`) | the origin's IndexedDB (`awake-bundle-keys-v1`) | `IndexedDbBundleKeyStore` storing structured-cloned WebCrypto `CryptoKey`s (`extractable == false`) |
 * | Desktop | none: Core never writes raw software keys to disk by default (D42) | `null` |
 * | Android | none: hardware-backed `AndroidKeyStore` wrapping belongs to the app's own store | `null` |
 * | iOS | none: Keychain access groups and protection classes belong to the app's own store | `null` |
 *
 * [namespace] follows the same safe-name rules as [platformContentCache]: 1 to 128 ASCII letters,
 * digits, `.`, `_` or `-`, starting with a letter or digit, not ending with `.`, and not a reserved
 * Windows device name.
 *
 * @param namespace A name for the app or project, unique to it and stable across releases.
 * @return A non-extractable platform key store where available, or null otherwise.
 * @throws IllegalArgumentException when [namespace] is not one safe folder name.
 */
fun platformBundleKeyStore(namespace: String): BundleKeyStore? {
    requireContentCacheNamespace(namespace)
    return createPlatformBundleKeyStore(namespace)
}

internal expect fun createPlatformBundleKeyStore(namespace: String): BundleKeyStore?

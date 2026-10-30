# Encrypted content

<p class="awake-lede">Seal a published project's bundle members with a key your game gets after sign-in, so bundles on a public CDN, in an app package or in the browser's cache are ciphertext to anyone without it.</p>

<div class="awake-badges" markdown>
<span class="awake-badge">module: <code>com.awakekt.awake:project</code></span>
<span class="awake-badge">Desktop · Android · iOS · Web</span>
</div>

[Content bundles](content-bundles.md) can be encrypted. Each distinct member is sealed on its own
with AES-256-GCM, so range reads still fetch and check one member, and the same files and key give
byte-identical bundles, so unchanged bundles keep their URLs. The engine checks each member as it
reads it, and the `ContentCache` only ever holds sealed bytes. The decision record is
[D42](https://github.com/awakekt/awake/blob/main/docs/architecture/decisions/D42-content-bundle-encryption.md).

## What it protects

Encryption stops **casual extraction**: scraping a CDN, unpacking an app, reading bundles or the
browser's IndexedDB, opening files in a generic viewer. It doesn't stop a player who holds the key,
and nothing stops a frame debugger from reading decoded textures off the GPU. How strong it is
depends only on where the key comes from:

| Key delivery | Strength |
| --- | --- |
| Online: your endpoint answers the key after sign-in | Bundles on a public CDN are ciphertext to anyone not signed in. |
| Offline after a first online sign-in: kept in the platform's key store | As strong as that store. |
| Fully offline: the key ships inside the client | **Deterrence only.** Anyone who unpacks the client can recover the key. |

## Seal bundles when you publish

Give `ContentBundleWriter` a `BundleEncryptionKey`: 32 random bytes and an id that the index names.
Read the key from a file or a CI secret. Never put it in a build script or the repository.

```kotlin title="Kotlin"
--8<-- "awake/project/src/commonTest/kotlin/com/awakekt/awake/project/EncryptedContentDocsSampleTest.kt:write"
```

Make a key once per project or entitlement tier, for example with `openssl rand 32 > bundle.key`, and
keep it in your secret store. Rotating a key re-seals every bundle and changes every URL, so don't
rotate it per session. To revoke a player, stop answering them at your key or index endpoint.

## Online: fetch the key after sign-in

Pass a `BundleKeyProvider` to `IndexedAssetSource` or `IndexedAssetSource.open`. The engine asks it
once per source for the index's key id. If the request fails, the read fails, and the next read
asks again.

```kotlin title="Kotlin"
--8<-- "awake/project/src/commonTest/kotlin/com/awakekt/awake/project/EncryptedContentDocsSampleTest.kt:online"
```

The key endpoint is yours. A minimal one in Ktor checks the session and answers the raw key:

```kotlin title="Kotlin (Ktor server, an example only)"
routing {
    authenticate("session") {
        get("/content-keys/{keyId}") {
            val keyId = call.parameters.getOrFail("keyId")
            val player = call.principal<PlayerSession>()!!
            if (!entitlements.canPlay(player, keyId)) return@get call.respond(HttpStatusCode.Forbidden)
            call.response.header(HttpHeaders.CacheControl, "no-store")
            call.respondBytes(secrets.bundleKey(keyId), ContentType.Application.OctetStream)
        }
    }
}
```

Serve it over HTTPS only, never cache it, and keep the bundles themselves on a public CDN with
`Cache-Control: public, max-age=31536000, immutable`.

## Offline after a first sign-in

Keep the key the first sign-in got, and answer it from the device later:

```kotlin title="Kotlin"
--8<-- "awake/project/src/commonTest/kotlin/com/awakekt/awake/project/EncryptedContentDocsSampleTest.kt:offline"
```

Core doesn't store keys. Back the store with your platform's key store:

| Platform | Store |
| --- | --- |
| Android | Wrap the key with an Android Keystore key, and keep the wrapped bytes in your app's storage. |
| iOS | The Keychain, with `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`. |
| Desktop | The OS credential store: Keychain on macOS, Credential Manager on Windows, Secret Service on Linux. |
| Web | A non-extractable WebCrypto `CryptoKey` kept in IndexedDB, used to wrap the bundle key. |

Choose how long a kept key lives, and when to ask the server again, by your own product rules.

## Fully offline builds

A build that must play with no network can ship the key in the client:
`BundleKeyProvider { embeddedKey }`. Only do this knowing what it means: the key is in the app
package, so encryption only slows an extractor down. It doesn't stop them.

## The cache

`IndexedAssetSource` puts each member's **sealed** bytes in the `ContentCache`, under a key that
`ProjectIndex.cacheKeyOf` derives from the key id and the member's SHA-256. Every cache hit is
decrypted and checked again, and a hit that fails to decrypt is fetched again. A
`FileSystemContentCache` or the browser's IndexedDB cache therefore holds nothing readable, and a
signed-out player can't read it. A cache of your own must prune by `ProjectIndex.cacheKeyOf`, not by
the entry's `sha256`.

## Format

**Bundles.** An encrypted bundle is format version 2: the header's reserved field holds flags, and
bit 0 (members encrypted) is always set. Each table record's size is the sealed size, the
plaintext's size plus a 16-byte tag, and its SHA-256 is the plaintext's. Each member's AES key and
nonce are the 44 bytes of `HKDF-SHA256(key, salt = keyId, info = "awake-awkb-v2" 0x00 sha256)`. An
engine that reads only version 1 refuses a version 2 bundle with a version error.

**The index.** An encrypted index is `formatVersion` 4. It adds
`"encryption": { "keyId": "…", "scheme": "aes-256-gcm-hkdf-sha256" }`, and every entry is a bundle
member. An engine that reads only versions 2 and 3 refuses it.

## See also

- [Content bundles](content-bundles.md)
- [Protected content](protected-content.md)

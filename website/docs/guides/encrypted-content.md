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
| Session-wrapped (`BundleKeyProvider.wrapped` + `BundleEncryptionKey.wrapFor`) | Wrapped per session with RSA-OAEP-SHA256; in browsers the unwrapped `HKDF` `CryptoKey` stays `extractable = false` and never enters Wasm linear memory. |
| Online raw bytes (`BundleKeyProvider { … }`) | Bundles on a public CDN are ciphertext to anyone not signed in. |
| Saved after a first online sign-in (`BundleKeyStore`) | As strong as the store (`platformBundleKeyStore` stores structured-cloned non-extractable `CryptoKey`s in browser IndexedDB; native apps supply Keystore/Keychain stores). |
| Fully offline: the key ships inside the client | **Deterrence only.** Anyone who unpacks the client can recover the key. |

Two limits hold whatever the key source:

- **Membership can be confirmed.** Sealing is deterministic and each bundle keeps the plaintext
  SHA-256 of its members, so anyone who already has a file can confirm it is in your bundles, as the
  index already allows. Nobody learns a file they don't have.
- **A new key needs a new key id.** Cached copies are kept by key id, so give every key its own id;
  a key rotated under the same id makes every cached copy fetch again.

## Seal bundles when you publish

Give `ContentBundleWriter` a `BundleEncryptionKey`: 32 random bytes and an id that the index names.
Read the key from a file or a CI secret. Never put it in a build script or the repository.

```kotlin title="Kotlin"
--8<-- "awake/project/src/commonTest/kotlin/com/awakekt/awake/project/EncryptedContentDocsSampleTest.kt:write"
```

With the CLI or the Gradle task, name the key and say where to read it. A key file holds the 32 raw
bytes or 64 hex digits; an environment variable holds the hex form:

```bash
awake bundle build/project.index.json --output build/published \
  --key-id harbor-town-2026 --key-file ~/.secrets/harbor.key   # or --key-env AWAKE_BUNDLE_KEY
```

```kotlin title="build.gradle.kts"
projectContent {
    bundleKeyId.set("harbor-town-2026")
    bundleKeyEnvironmentVariable.set("AWAKE_BUNDLE_KEY") // or bundleKeyFile.set(file(...)), outside the repository
}
```

`generateContentBundles` tracks only a SHA-256 of the key, so a new key reruns it and Gradle never
stores the key. The CLI and the task write byte-identical bundles for the same key.

Make a key once per project or entitlement tier, for example with `openssl rand 32 > bundle.key`, and
keep it in your secret store. Rotating a key re-seals every bundle and changes every URL, so don't
rotate it per session. To revoke a player, stop answering them at your key or index endpoint.

When one published index spans shared assets and gated regions or expansions, give gated bundle groups
their own keys while keeping shared bundles on the default key:

```kotlin title="Kotlin"
--8<-- "awake/project/src/commonTest/kotlin/com/awakekt/awake/project/EncryptedContentDocsSampleTest.kt:group-keys"
```

With the CLI or `generateContentBundles`, override a group's key by group name:

```bash
awake bundle build/project.index.json --output build/published \
  --key-id common-2026 --key-file ~/.secrets/common.key \
  --group-key-file scenes=north-island-2026:~/.secrets/north-island.key
```

```kotlin title="build.gradle.kts"
projectContent {
    bundleKeyId.set("common-2026")
    bundleKeyEnvironmentVariable.set("AWAKE_COMMON_KEY")
    bundleGroupKey("scenes", keyId = "north-island-2026", environmentVariable = "AWAKE_NORTH_ISLAND_KEY")
}
```

With a [public sign-in set](protected-content.md), only the full set is sealed:
`generatePublicContentBundles` never takes the key, because the sign-in scene loads before the player
has it. Open the sealed set with your `BundleKeyProvider` after sign-in and attach it to the
`LayeredAssetSource`; the key is asked for on the first read that needs it.

## Online: session-wrapped keys (recommended)

Use `BundleKeyProvider.wrapped` so your client generates an ephemeral RSA-2048-OAEP-SHA256 keypair,
sends its X.509 `SubjectPublicKeyInfo` DER public key to your authenticated key endpoint, and unwraps
the response directly into an opaque `BundleKeyHandle`. On Web (`wasmJs`), SubtleCrypto unwraps the
bundle key into a non-extractable `HKDF` `CryptoKey` (`extractable = false`) and derives and opens
every member inside SubtleCrypto, so neither the 32-byte master key nor any derived AES key enters
`WebAssembly.Memory`:

```kotlin title="Kotlin"
--8<-- "awake/project/src/commonTest/kotlin/com/awakekt/awake/project/EncryptedContentDocsSampleTest.kt:wrapped"
```

On the server, wrap the `BundleEncryptionKey` for the client's SPKI public key with `wrapFor`
(`RSA-OAEP` with `SHA-256` digest, `MGF1-SHA256`, and empty OAEP label):

```kotlin title="Kotlin (Ktor server, an example only)"
routing {
    authenticate("session") {
        post("/content-keys/{keyId}/wrap") {
            val keyId = call.parameters.getOrFail("keyId")
            val player = call.principal<PlayerSession>()!!
            if (!entitlements.canPlay(player, keyId)) return@post call.respond(HttpStatusCode.Forbidden)
            val clientSpki = call.receive<ByteArray>()
            val wrapped = secrets.bundleEncryptionKey(keyId).wrapFor(clientSpki)
            call.response.header(HttpHeaders.CacheControl, "no-store")
            call.respondBytes(wrapped, ContentType.Application.OctetStream)
        }
    }
}
```

Serve it over HTTPS only, never cache it, and keep the bundles themselves on a public CDN with
`Cache-Control: public, max-age=31536000, immutable`.

If your host already receives raw 32-byte keys over an authenticated channel, the SAM form of
`BundleKeyProvider` works as well; the engine asks it once per distinct `keyId` per source, only when
a bundle sealed under that `keyId` is read or prefetched:

```kotlin title="Kotlin"
--8<-- "awake/project/src/commonTest/kotlin/com/awakekt/awake/project/EncryptedContentDocsSampleTest.kt:online"
```

## Offline play

In a browser, pass `platformBundleKeyStore(namespace)` to `BundleKeyProvider.wrapped(store)`:
`IndexedDbBundleKeyStore` persists unwrapped WebCrypto `CryptoKey` handles in IndexedDB via HTML
structured clone while keeping `extractable = false`, so return visits open cached encrypted bundles
offline without storing raw key bytes. On desktop, Android, and iOS, `platformBundleKeyStore` returns
`null` so Core never writes raw software keys to disk by default; pass a `BundleKeyStore` backed by
your app's `AndroidKeyStore`, iOS Keychain, or OS credential store, or supply a custom
`BundleKeyProvider`:

```kotlin title="Kotlin"
--8<-- "awake/project/src/commonTest/kotlin/com/awakekt/awake/project/EncryptedContentDocsSampleTest.kt:offline"
```

A build that must play with no network at all can return a key shipped in the client:
`BundleKeyProvider { embeddedKey }`. **This is deterrence only.** The key is in the app package, so
encryption slows an extractor down but doesn't stop them.

## The cache

`IndexedAssetSource` puts each member's **sealed** bytes in the `ContentCache`, under a key that
`ProjectIndex.cacheKeyOf` derives from the key id and the member's SHA-256. Every cache hit is
decrypted and checked again. A hit that fails to decrypt isn't returned: the read fetches the member
and replaces the cached copy once the fetched one decrypts, so offline the old copy stays. A
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
`"encryption": { "keyId": "…", "scheme": "aes-256-gcm-hkdf-sha256" }` as the default key id, each
bundle in `bundles` may set `"keyId"` to override it for that bundle's members, and every entry is a
bundle member. An engine that reads only versions 2 and 3 refuses it.

## See also

- [Content bundles](content-bundles.md)
- [Protected content](protected-content.md)

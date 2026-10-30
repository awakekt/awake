# D42: content bundle encryption

Status: accepted (2026-10-30). Promotes the content bundle encryption design note and implements
layers 1 to 3 of [#1149](https://github.com/awakekt/awake/issues/1149).

## Threat boundary

Encrypting a published project's bundle members stops **casual extraction**: scraping a public
CDN, unpacking an app, reading `.awkb` files or the browser's IndexedDB cache, and opening the files
in generic asset viewers. It does not stop a signed-in player who holds the key in memory, and no
engine stops a frame debugger reading decoded textures from the GPU. The strength depends only on
where the key comes from:

| Key delivery | What it is |
|---|---|
| From the game's own authenticated endpoint after sign-in | Protection: content on a public CDN is ciphertext to anyone not signed in. |
| Kept on the device after a first online sign-in | Protection at rest, as strong as the platform store the app keeps it in. |
| Shipped inside the client (fully offline builds) | **Deterrence only.** Anyone who unpacks the client can recover the key. |

## Decision

1. **Members are encrypted one by one, not the bundle.** Each distinct member payload is sealed with
   AES-256-GCM (12-byte nonce, 16-byte tag appended), so an HTTP range read still fetches and
   authenticates one member alone. Crypto goes through cryptography-kotlin and each platform's
   provider (JCA, Apple CryptoKit/CommonCrypto, WebCrypto), never hand-written code, as in
   [D41](D41-plugin-trust-formats.md). WebCrypto is asynchronous, so every seal and open is
   `suspend`.
2. **Key and nonce are derived, so bundles stay deterministic.** For a member whose plaintext
   SHA-256 is `h` (lowercase hex), the 44 bytes
   `HKDF-SHA256(ikm = masterKey, salt = UTF-8(keyId), info = "awake-awkb-v2" 0x00 UTF-8(h))` are the
   AES key (first 32) and the nonce (last 12). A key is used for one plaintext only, so a nonce never
   repeats under a key, and the same files and key give byte-identical bundles, the same SHA-256 and
   the same URL. The master key is 32 random bytes. No associated data.
3. **Bundle format version 2, with a flags field.** Version 1 bundles are unchanged, and a writer
   with no key still writes them. Version 2 turns the reserved header field into flags; bit 0,
   *members encrypted*, is the only one defined and is always set, and a reader refuses any other
   flags, so a later flag needs no new version but an engine that predates it still refuses it.
   In an encrypted bundle a table-of-contents record's size is the stored size (plaintext size + 16)
   and its SHA-256 is the plaintext's, which derives the member's key. A version 1 reader refuses a
   version 2 bundle with its existing version error.
4. **Project index format version 4.** An encrypted index sets
   `encryption = { "scheme": "aes-256-gcm-hkdf-sha256", "keyId": "<id>" }` and never carries the key.
   Every entry is a bundle member; `sizeBytes` and `sha256` stay the plaintext's, and the stored
   bytes run `sizeBytes + 16` from `offset`. An engine that reads only versions 2 and 3 refuses it.
5. **Core takes the key, the app owns the policy.** `BundleKeyProvider` is a `suspend (keyId) ->
   ByteArray` that `IndexedAssetSource` and `IndexedAssetSource.open` take. It is asked once per
   source for the index's key id; a failure is not remembered, so the next read asks again. An
   encrypted index without a provider fails when the source is built. How the key is fetched,
   stored, rotated or revoked is the app's; Core names no endpoint and holds no account.
   `:awake:project` stays headless.
6. **The cache holds ciphertext (option B).** `ContentCache` gets each member's stored bytes, under
   a key derived from the key id and the plaintext SHA-256 (`ProjectIndex.cacheKeyOf`), and every
   hit is opened, which authenticates it, before a parser sees it. Nothing readable sits at rest, in
   IndexedDB or on disk. A hit that fails to open isn't returned: the read fetches the member, and
   the cached copy is replaced only once a fetched copy opens, so offline it stays. The cost is a
   decrypt per read and no sharing of cached bytes with loose or plaintext releases of the same file.
7. **No key store in Core (considered, deferred).** One contract covers every key source because the
   app decides what the provider does: fetch from its own server after sign-in (the priority path),
   return a key it saved earlier, or return a key embedded in the client. A Core store backed by
   platform secure storage (an Android Keystore-wrapped key, the iOS Keychain, the desktop OS
   credential store, a non-extractable WebCrypto `CryptoKey` in IndexedDB) was considered and
   deferred: each target needs its own platform API, and the right lifetime and revocation rule is
   the product's. It is tracked on #1149.
8. **Keys scope to a project or an entitlement tier, not a session.** Rotating a key re-encrypts
   every bundle and changes every URL. Revoke a session at the key or index endpoint instead.

## Limits accepted

- **Membership can be confirmed.** Sealing is deterministic and each table of contents record keeps
  the plaintext SHA-256, so anyone who already has a file can confirm it is in a bundle, as the
  index already lets them. Nobody learns a file they don't have.
- **A new key needs a new key id.** `cacheKeyOf` derives from the key id, not the key, so a key
  rotated under the same id leaves cached copies that no longer open; each is fetched again before
  it is replaced. Give every key its own id.

## Writer side

`ContentBundleWriter`, `awake bundle` and `generateContentBundles` take an optional
`BundleEncryptionKey(keyId, bytes)`. The CLI and the Gradle task read the key from a file or an
environment variable, never from a build script or the repository.

## Left out

| Left out | Where |
|---|---|
| Platform-backed offline key store | #1149, Follow-ups |
| Cooked engine formats (KTX2/Basis, engine mesh layout) | #1149, layer 4 |
| Obfuscation guidance, watermarking | #1149, layers 5 and 6 |
| Encrypting loose (unbundled) files | Not planned: an encrypted index is bundled. |

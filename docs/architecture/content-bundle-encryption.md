# Design Note: Optional Content Bundle Encryption

Status: proposed design note, not scheduled ([#1038](https://github.com/awakekt/awake/issues/1038)). Decided separately before any implementation.

## Context and threat boundary

Content bundles (`ContentBundleFormat`, `.awkb`, `#1032`) pack published project files into content-addressed archives verified by SHA-256, optionally hiding readable paths behind `ProjectPathIds` and authorizing requests through `TokenAuthorizedFetch` (`#1036`).

As recorded in [network.md](../plans/network.md#out-of-scope-asset-delivery), **encrypting client-side assets with a key shipped in the client binary is non-viable DRM**: any key embedded in desktop, mobile, or WebAssembly code is recoverable by anyone running the client.

There is one narrower deployment problem where optional bundle encryption **with a runtime-delivered key** does change the cost and security posture:

- **Public static CDN hosting without per-request edge authorization.** With `TokenAuthorizedFetch` alone, every bundle or range `GET` must pass through an authorizing server or edge Worker (or use short-lived CDN signed URLs). Encrypting `.awkb` payloads at publish time lets the host place bundles on an unauthenticated public bucket or CDN with `Cache-Control: public, max-age=31536000, immutable`, while delivering the release decryption key once from the authenticated sign-in / `ReadToken` endpoint.
- **What it protects:** unauthenticated scraping of the public CDN before sign-in yields only ciphertext.
- **What it still does not protect:** a signed-in player holds the key in client memory and can inspect decrypted assets. That limit is fundamental and accepted.

## Candidate wire and API shape

### 1. Encrypt per distinct member payload, not the whole `.awkb` file

Encrypting an entire `.awkb` archive as one AEAD blob prevents single-member HTTP `Range` reads (`IndexedAssetSource(..., rangeFetch = ...)`), because a range read could neither authenticate nor decrypt one member without downloading the whole archive.

Instead, encrypt **each distinct member payload** stored in the bundle's data region:

- **Cipher:** `AES-256-GCM` (12-byte nonce, 16-byte authentication tag) via platform-native providers (`WebCrypto` on `wasmJs`, `CommonCrypto` / CryptoKit on Apple, JCA on JVM/Android) through `cryptography-kotlin`, matching the zero-hand-rolled-crypto rule in [D41](decisions/D41-plugin-trust-formats.md). Because `WebCrypto` is asynchronous, decryption is `suspend` — `IndexedAssetSource.read` and `BundleMembers.load` are already `suspend`.
- **Deterministic key and nonce derivation:** derive a per-member key/nonce pair via `HKDF-SHA256(ikm = masterBundleKey, salt = keyIdUtf8, info = "awake-awkb-v2\u0000" + plaintextSha256)`.
  - Because each distinct payload in a bundle is already deduplicated by `plaintextSha256`, `(masterBundleKey, keyId, plaintextSha256)` is never reused on two different plaintexts under the same key/keyId.
  - Because derivation is deterministic for a fixed `(masterBundleKey, keyId)`, `ContentBundleWriter` still produces **byte-identical** `.awkb` archives across builds, preserving content-addressed URLs (`bundles/<ciphertextBundleSha256>.awkb`) and incremental uploads.
- **Range reads:** `ProjectIndexEntry.offset` and a ciphertext length (`encryptedSizeBytes`, or `sizeBytes + 16`) address a single self-authenticating `AES-256-GCM` member slice, so HTTP `Range` requests continue to fetch and authenticate one member alone.

### 2. Format versioning

- Bump `ContentBundleFormat.VERSION` to `2` (or allocate a flags bit in the 2-byte reserved header field alongside version `2`) when member payloads are encrypted, so older engines fail fast with a clear version error rather than feeding ciphertext to asset decoders.
- Record `encryption = ProjectBundleEncryption(scheme = "aes-256-gcm-hkdf-sha256", keyId = "<id>")` on `ProjectIndex` (`formatVersion = 3` optional field, or `formatVersion = 4`), leaving the secret key itself **out** of the index and bundles.

### 3. Key delivery with `ReadToken`

Keep key-distribution policy out of `:awake:project`, matching `TokenAuthorizedFetch`:

- Extend `ReadToken` with an optional key ring (`keys: Map<String, ByteArray> = emptyMap()`, redacted in `toString()` just like `ReadToken.value`) or accept a `BundleKeyProvider` (`suspend (keyId: String) -> ByteArray`) on `IndexedAssetSource.open`.
- When the index is served from the authenticated API alongside `ReadToken` and bundles sit on a public CDN, the game fetches `{ indexUrl, token, bundleKey, keyId }` once after sign-in: the index uses `TokenAuthorizedFetch` (or arrives inline), while bundle `GET`s hit the public CDN directly with no `Authorization` preflight.

### 4. `ContentCache` and key-rotation trade-offs

Two design choices pull against caching and must be weighed explicitly if implemented:

| Question | Option A: Cache plaintext by `plaintextSha256` | Option B: Cache ciphertext before decrypting |
| --- | --- | --- |
| Cross-release & loose/bundled reuse | Shared across key rotations and loose indexes; zero decrypt cost on cache hit. | Re-decrypts on every read; key rotation invalidates cached ciphertext if re-encrypted. |
| Browser `IndexedDB` contents | Plaintext sits in the origin's `IndexedDB` after an authorized player loads it once. | Only ciphertext sits at rest in `IndexedDB` when signed out. |

Key rotation also trades off against CDN immutability: rotating `masterBundleKey` changes every bundle's ciphertext SHA-256 and re-uploads all bundles, so keys should scope to a project or entitlement tier rather than rotating per short-lived session token (session revocation belongs at the index/token endpoint, not by re-encrypting gigabytes of static bundles).

## Gate to implement

Keep this unimplemented until a real deployment hits per-request edge-authorization cost or CDN limits that `ContentBundleWriter` (16–32 MiB bundles) + `TokenAuthorizedFetch` do not already solve. When that happens, promote this note to an architecture decision record (`docs/architecture/decisions/D*.md`) locking the cache mode (`plaintext` vs `ciphertext`) and `ProjectIndex` version before writing code.

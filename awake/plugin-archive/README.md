# :awake:plugin-archive

`com.awakekt.awake:plugin-archive`, package `com.awakekt.awake.pluginarchive`: how an Awake plugin is
described, packaged and trusted. It holds formats and checks only; installing, resolving,
catalogues and which keys to trust are host code ([D41](../../docs/architecture/decisions/D41-plugin-trust-formats.md)).
It depends on no editor, UI, scene or GPU module, so a played project can read `plugins.lock.json`.

| Type | What it is |
|---|---|
| `PluginManifest`, `PluginDependency` | `plugin.json`. |
| `PluginArchive`, `PluginArchiveReader`, `PluginArchiveException` | The `.awakeplugin` zip: `plugin.json`, the payload, and `signature.sig`. At most 4096 entries and 64 MiB unpacked, safe unique relative paths, stored or deflated entries only. |
| `PluginArchiveDigest`, `PluginArchiveVerifier`, `PluginArchiveVerification`, `PluginArchiveSigner` | The canonical archive digest, and making and checking the publisher's signature over it. |
| `AwakeRootSignature`, `RootSignedDocument` | The root key's signature domains and the check of documents it signs. |
| `PublisherKeys`, `PublisherKeyList`, `SignedPublisherKeyList` | The versioned list of publishers' public keys. |
| `RevokedPluginVersion`, `PluginRevocationList`, `SignedPluginRevocationList` | The versioned list of revoked plugin versions. |
| `PluginTrust`, `PluginVerificationResult` | One decision from both lists: verified by publisher, unsigned (with reason), bad signature, or revoked. |
| `PluginRuntimeModule`, `LockedPlugin`, `AwakePluginsLock` | `plugins.lock.json`: the exact plugins a project uses and the Maven artifacts their runtime needs. |

## Wire formats

- **Archive signature.** `signature.sig` holds the raw RSASSA-PKCS1-v1_5 / SHA-256 (`SHA256withRSA`)
  signature over `"awake-plugin-archive-v1\n" + hex(canonicalDigest)`. The digest is SHA-256 over
  every entry except `signature.sig`, sorted by name, each name and content prefixed by its 4-byte
  big-endian length. `PluginArchiveSigner` writes it (below).
- **Root-signed documents.** `{"document": "<exact JSON string>", "signature": "<base64>"}`. The
  signature covers `"<domain tag>\n" + document`, tags `awake-publisher-key-list-v1` and
  `awake-plugin-revocation-list-v1`, so a signature for one kind of document never verifies as
  another. Hosts keep the highest `version` they have verified and refuse an older list.
- **Keys.** X.509 `SubjectPublicKeyInfo`, PEM or bare base64 DER (`openssl rsa -pubout`), RSA 2048
  bits or more.

## Calling the suspend checks

Every check runs on the platform's own provider through
[cryptography-kotlin](https://github.com/whyoleg/cryptography-kotlin): the JDK on desktop and
Android, Apple's Security and CommonCrypto on iOS, WebCrypto in the browser. WebCrypto only has
asynchronous calls, so `PluginArchiveReader.read`, `PluginArchiveVerifier.verify`, `PluginTrust.verify`,
`PluginArchiveDigest.canonicalDigest`, `AwakeRootSignature.isValid` and the `Signed*.verify` functions
suspend on every target.

```kotlin
// Desktop or Android: off the UI thread.
val result = withContext(Dispatchers.Default) {
    val publishers = when (val keys = signedKeys.verify(rootPublicKeys)) {
        is RootSignedDocument.Verified -> keys.value
        is RootSignedDocument.Rejected -> error(keys.reason)
    }
    PluginTrust.verify(PluginArchiveReader.read(bytes), publishers, revocations)
}
```

- **Desktop and Android:** call from a coroutine on `Dispatchers.Default` or `IO`. Code with no
  coroutine can use `runBlocking`, never on the UI thread.
- **iOS:** call from a Kotlin coroutine. Swift sees each as an `async` function, or one taking a
  completion handler, through the Objective-C export.
- **wasmJs:** call from a coroutine (`MainScope().launch { ... }`). The browser has no blocking form.

## Signing

`PluginArchiveSigner` is the publisher side, common to every target: `suspend signature(entries, key)`
returns the `signature.sig` bytes and `suspend signedEntries(entries, key)` adds them, replacing any
old signature. The key is the publisher's RSA private key as PKCS#8, PEM or bare base64 DER
(`openssl genpkey`). Signing keys belong on a publisher's machine or a signing service, never in a
shipped app.

On the desktop JVM, the blocking `PluginArchiveSigner.sign(archiveBytes, key)` rewrites a whole
archive after the same checks `PluginArchiveReader` applies, and `PluginArchiveSignerMain` signs a
file from a build, in its own JVM:

```
AWAKE_PLUGIN_SIGNING_KEY="$(cat private.pem)" java -cp <plugin-archive-desktop and its dependencies> \
  com.awakekt.awake.pluginarchive.PluginArchiveSignerMain build/plugin.awakeplugin
```

`templates/plugin`'s `signAwakePlugin` task does exactly this after `awakePlugin`.

## Changing this module

`./gradlew :awake:plugin-archive:allTests :awake:plugin-archive:apiDump`, and review the dump. A
change to a wire format needs a new domain tag or format version, never an edit in place: existing
signatures must keep verifying, which the openssl-made fixtures in `commonTest` pin.

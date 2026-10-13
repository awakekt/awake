- **A plugin archive can be signed and checked against its publisher.** `PluginManifest` gains a
  reverse-domain `publisher` id, validated separately from the free-text `author`. The plugin
  template's `awakePlugin` task signs the archive's canonical digest (every entry except
  `signature.sig`, sorted by name) with `SHA256withRSA` when `AWAKE_PLUGIN_SIGNING_KEY` (PEM or
  base64 PKCS#8, never from the repository) or `-Pawake.plugin.signingKey` is set, and writes
  `signature.sig` into the archive; with no key it still packages, unsigned, and says so. The
  editor contract's new `PluginArchiveVerifier.verify` checks that signature in pure Kotlin
  (`com.ionspin.kotlin:bignum` for the RSA public-key operation), so any host can check a plugin on
  every Awake target, including wasmJs and iOS, without a platform crypto provider.

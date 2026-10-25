# Awake plugin template

A starter editor plugin for Awake Studio: a weather panel, a rain toggle, and a rain cloud entity
template. Compiles against `com.awakekt.awake.editor:contract` only; the host supplies everything
else at run time.

## Build

```
./gradlew :plugin:awakePlugin
```

Produces `plugin/build/awakeplugin/<group>-<version>.awakeplugin`.

## Publisher identity

Set `plugin.json`'s `id` and `publisher` to your own reverse-domain ids (e.g. `com.yourstudio`)
before distributing the plugin. `author` stays free text for display; `publisher` is what a host's
trust registry looks up a signing key by.

## Signing the archive

`awakePlugin` signs the archive's contents with an RSA private key read from the
`AWAKE_PLUGIN_SIGNING_KEY` environment variable (PEM or base64 PKCS#8), or the
`awake.plugin.signingKey` Gradle property for local testing. Never put the key in the repository.

```
export AWAKE_PLUGIN_SIGNING_KEY="$(cat path/to/private-key.pem)"
./gradlew :plugin:awakePlugin
```

Generate a key pair with:

```
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out private.pem
openssl rsa -in private.pem -pubout -out public.pem
```

Give `public.pem` to hosts that need to verify your plugin (a host checks it with
`com.awakekt.awake.pluginarchive.PluginArchiveVerifier` or `PluginTrust` from
`com.awakekt.awake:plugin-archive`). Without a signing key set,
`awakePlugin` still packages the archive, unsigned, and logs that it did.

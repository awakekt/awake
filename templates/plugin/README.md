# Awake plugin template

A starter editor plugin for Awake Studio: a weather panel, a rain toggle, and a rain cloud entity
template. Compiles against `com.awakekt.awake.editor:contract` only; the host supplies everything
else at run time.

## Build

```
./gradlew :plugin:awakePlugin
```

Produces `plugin/build/awakeplugin/<group>-<version>.awakeplugin`.

## Adding scene components

When a plugin also defines runtime scene components, it can generate their bindings instead of writing
them by hand. Add these to the existing module's plugins and dependencies, using a Core release that
includes the published scene-components processor:

```kotlin
plugins {
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.awake.scene.components)
}
awakeSceneComponents { registry.set("com.yourstudio.weather.WeatherSceneComponents") }
kotlin.sourceSets.commonMain.dependencies { implementation(libs.awake.scene.binding) }
```

Annotate the serializable schema with `@SceneBinding(LiveComponent::class)`. The generated
`WeatherSceneComponents.bindings` belongs in the host's scene capability registration; generation
does not register it globally. The processor is a build dependency and is not bundled into the plugin
archive. Its version defaults to the build plugin's SDK release, not this plugin project's own version.
The template's desktop and Wasm targets provide the shared metadata compilation the processor uses.

## Publisher identity

Set `plugin.json`'s `id` and `publisher` to your own reverse-domain ids (e.g. `com.yourstudio`)
before distributing the plugin. `author` stays free text for display; `publisher` is what a host's
trust registry looks up a signing key by.

## Signing the archive

`awakePlugin` packages the archive, and `signAwakePlugin`, which runs right after it, signs it with
an RSA private key read from the `AWAKE_PLUGIN_SIGNING_KEY` environment variable (PEM or base64
PKCS#8), or the `awake.plugin.signingKey` Gradle property for local testing. Never put the key in
the repository. The signer is Core's own `PluginArchiveSigner` from `com.awakekt.awake:plugin-archive`,
run in a separate JVM; it is a build tool here and not bundled with the plugin.

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

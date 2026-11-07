# Build codegen plugins: string keys and version stamp

Two public Gradle plugins from Awake's build-logic generate small Kotlin sources at build time, for
any Kotlin Multiplatform project. Both are published beside `release-cut` at the Core version, add
their output to `commonMain`, and put nothing in the generated code beyond the Kotlin standard
library, so the app takes no runtime dependency on Awake.

```kotlin
// build.gradle.kts of the module
plugins {
    id("com.awakekt.awake.plugin.string-keys") version "<the Core version you build against>"
    id("com.awakekt.awake.plugin.version-stamp") version "<the Core version you build against>"
}
```

They are two plugins, not one, because a module often needs only one and each has its own
configuration block.

## `com.awakekt.awake.plugin.string-keys`

Turns a JSON string table into a typed `object`, so code reads `Strings.menu.start` and never the raw
string `"menu.start"`.

```kotlin
stringKeys {
    packageName.set("com.example.app")   // required
    // sourceFile: src/commonMain/strings/en.json
    // objectName: Strings    keyTypeName: StringKey    strictKeys: false
}
```

`en.json` may be flat (`{"menu.start": "Start"}`), nested (`{"menu": {"start": "Start"}}`) or both;
nested objects flatten into dotted keys, in file order. The task `generateStringKeys` writes one file
to `build/generated/awake/stringKeys` and registers it as a `commonMain` source dir, so a compile runs
it. The file holds a `@JvmInline value class StringKey(val key: String)` and the `object`, whose
members are `StringKey` values holding the dotted key, with the string as their KDoc. Every
declaration is `public` explicitly, so it compiles under explicit API mode.

Segments that are not identifiers are escaped (`main-menu` becomes `main_menu`, `2nd` becomes `_2nd`),
or rejected when `strictKeys` is true. The build fails, naming every case, on:

- a value that is not a string, an empty group, or an empty segment (`a..b`);
- a key reached twice (a flat and a nested spelling of one path);
- two keys that escape to one identifier (`a-b` and `a_b`);
- a key that is both a leaf and a group (`menu` and `menu.start`);
- a segment equal to `keyTypeName`.

A repeated member inside one JSON object is not reported, because the JSON parser keeps the last one.
The plugin has no rule about what a value means; check references between tables in your own build.

Without the Kotlin Multiplatform plugin, add `tasks.named("generateStringKeys")` to your source set's
`srcDir` yourself.

## `com.awakekt.awake.plugin.version-stamp`

Generates an `object` with `const val VERSION` and `const val COMMIT`.

```kotlin
version = "1.2.0-rc.1"
versionStamp {
    packageName.set("com.example.app")   // required
    // objectName: VersionStamp    version: project.version    commit: git rev-parse --short HEAD
}
```

`VERSION` must be `X.Y.Z`, `X.Y.Z-beta.N` or `X.Y.Z-rc.N`; anything else fails the build. These order
as beta, then rc, then stable (`ReleaseVersion` in build-logic). `COMMIT` comes from a Gradle value
source, so it is configuration-cache safe and is re-read each build, and it is `unknown` outside a git
checkout or without git. The task is `generateVersionStamp`; its output is in
`build/generated/awake/versionStamp`.

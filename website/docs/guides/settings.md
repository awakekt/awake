# Settings

<p class="awake-lede">Declare each player setting once, with its default, range and label. Settings are saved when the player changes one, read back the same way on every platform, overridden for a session by a launch option, and bound to the UI with no glue code.</p>

<div class="awake-badges" markdown>
<span class="awake-badge">module: <code>com.awakekt.awake.core:settings</code></span>
<span class="awake-badge">Desktop · Android · iOS · Web</span>
</div>

A player setting resolves in one order, declared once on its key rather than at each call site:

1. its **default**,
2. the player's **saved** value, when there is one,
3. the **launch option** that overrides it, when the launch gave one (see [Launch options](launch-options.md)).

The launch option wins for that session only. It is never saved, and a change the player makes during
the session replaces it.

!!! danger "Settings are not for secrets"
    Never keep a token, a password, a session or refresh token, or a sign-in in a setting. Settings
    are saved as plain text and are meant to be seen:

    - **On the web** they are in IndexedDB, where any script on the page can read them, so one
      cross-site scripting bug or compromised third-party script leaks them.
    - **On desktop and Android** they are in `settings.json`, readable by other software running as
      the same user and copied into backups and support bundles. A copy set aside as
      `settings.json.unreadable` holds them too.
    - **A refused value is printed** in its `SettingsProblem`, and problems are meant to be logged.
    - **A generated settings window shows every key**, through `keys` and `text(key)`.

    Keep secrets in the platform's credential store (the iOS Keychain, the Android Keystore, a
    non-extractable WebCrypto key on the web, the OS credential store on desktop), never in a
    `SettingsSchema`.

## Declare the settings

```kotlin title="commonMain"
--8<-- "samples/engine-showcase/src/commonTest/kotlin/com/awakekt/awake/showcase/docs/SettingsDocsSampleTest.kt:schema"
```

| Declaration | Value | Refused |
| --- | --- | --- |
| `boolean` | on or off | — |
| `int`, `float` | a number within `range` | outside the range |
| `string` | text | empty, or what `check` refuses |
| `choice` | one of the enum's entries | any other name |
| `setting` | any `ValueType`, with your own `check` | what the type or `check` refuses |

A setting and the launch option that overrides it have the same type, so the compiler checks
`override =`. The setting's own range applies to the launch value too: a launch value the setting
refuses is reported and not used.

`formerly` lists names the setting was saved under before. When settings are opened, a value under an
old name is read, then saved under the new name, so renaming a key never loses a player's setting.

## Open them

```kotlin title="commonMain"
--8<-- "samples/engine-showcase/src/commonTest/kotlin/com/awakekt/awake/showcase/docs/SettingsDocsSampleTest.kt:open"
```

`FileSettingsStorage` keeps the settings in `settings.json` in a `FileSystem`.
`createPlatformFileSystem(root)` from `awake:core:io` puts that file in browser storage (IndexedDB)
on the web, and in a file under `root` on desktop and Android (on Android, use the app's files
directory).

- **Reads allocate nothing.** `settings[key]` returns the value resolved when it last changed.
- **Writes never block a frame.** A change applies at once and is written after
  `saveDelayMillis` (500 ms by default) in the scope you pass, together with any changes that follow
  it. Call `flush()` when the game closes.
- **Only changed values are saved.** A value equal to its default is removed from the file, so when
  a later build changes a default, every player who never touched that setting gets the new one.
  `reset(key)` puts a setting back to its default.
- **Names the schema does not know are kept**, so settings saved by a newer build survive a run of
  an older one.

## Problems

Nothing in settings throws. `settings.problems` is a `StateFlow` of `SettingsProblem`s:

| Problem | When | Effect |
| --- | --- | --- |
| `Unreadable(reason)` | the saved text is not a JSON object | it is kept aside, as `settings.json.unreadable`, and every setting starts from its default |
| `Invalid(key, given, reason)` | a saved value, a launch value or a `set` the key refuses | the setting keeps the value it had; `set` returns false |
| `StorageFailed(cause)` | reading or writing the storage failed | the values in memory stay; the next change tries again |

## Bind them to the UI

Each key is a `StateFlow`, so the Compose runtime's `collectAsState()` (in `awake:compose:state`)
observes it and recomposes when it changes:

```kotlin title="commonMain"
val touch by settings.state(GameSettings.touch).collectAsState()
ShadcnSwitch(checked = touch, onCheckedChange = { settings[GameSettings.touch] = it })
```

A settings window can be generated from the schema. `GameSettings.keys` lists each setting with
its `label`, `description`, `min` and `max` (for a slider) and `choices` (for a picker), and
`settings.text(key)` and `settings.setText(key, text)` read and write any key without knowing its
type:

```kotlin title="commonMain"
--8<-- "samples/engine-showcase/src/commonTest/kotlin/com/awakekt/awake/showcase/docs/SettingsDocsSampleTest.kt:window"
```

## Another storage backend

`SettingsStorage` has three methods: `read()` and `write(text)`, which read and write all the
settings as one text, and `keepAside(text)`, for text that could not be read. The schema and the
`Settings` store never see the backend, so a different one is a small class. `MemorySettingsStorage`
is the one for tests.

### Why not multiplatform-settings

[multiplatform-settings](https://github.com/russhwolf/multiplatform-settings) (Apache-2.0) is a
well-maintained Kotlin Multiplatform key-value store. It covers Android (`SharedPreferences`), the
JVM (`Preferences`), Apple platforms (`NSUserDefaults`) and the browser (`localStorage`, JS and
wasmJs). It is not the default here, for two reasons:

- **It stores raw values.** Typed keys with ranges, the default–saved–launch resolution order,
  changed-only saving, renamed keys and the problem report would still be Awake code on top of it.
- **It would be a second storage path.** Awake already keeps files portably in `awake:core:io`
  (IndexedDB on the web), and settings stored there sit with the rest of a game's saved data.

A game that wants it anyway writes a `SettingsStorage` that keeps the whole text under one key of a
multiplatform-settings `Settings` instance. Nothing else changes.

## See also

- [Launch options](launch-options.md) for the options that override settings.
- [Launch parameters](launch-parameters.md) for `LaunchEnvironment.primaryInput`, a good platform
  default for a touch setting.

# Awake Compose Localization (`:awake:compose:localization`)

Bridge between **`:awake:core:localization`** and **`:awake:compose:runtime`**: Compose UI reads
its text, and the names of keys and buttons, from the active string table.

- **`LocalStrings`**: the `Strings` the UI below reads from.
- **`LocalInputLabels`**: the `InputLabels` the UI below names keys and buttons with.
- **`ProvideStrings(strings, inputLabels) { ... }`**: provides both.
- **`stringResource(key)`**, **`stringResource(key, "n" to 3)`**, **`stringResource(key, map)`**:
  the text in the active locale. A missing key, or no `LocalStrings`, shows the key and is logged
  once.
- **`inputLabels()`**: the provided labels, for button prompts and hotkey labels.

Awake Compose composes the whole tree every frame, so text read here shows a new locale on the
frame after `Strings.setLocale` returns. There is nothing to observe.

```kotlin
val strings = project.strings ?: Strings.load(StringTableSource.assets(files))
val labels = InputLabels.load(strings) // adds Core's key names, in the system's shortcut style

context(_: Composer)
fun Hud() {
    ProvideStrings(strings, labels) {
        Text(stringResource("hud.items", "n" to bag.count))
        Text(stringResource("hud.save", "keys" to inputLabels().chord(KeyChord(Key.S, setOf(KeyModifier.Ctrl)))))
    }
}
```

A plugin ships its own default text with `strings.addSource(pluginTables, StringLayer.Plugin)`;
the game's `strings/` override any of its keys. See the
[localization guide](../../../website/docs/guides/localization.md).

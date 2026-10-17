# Awake Core Localization

Text in the player's language. A project keeps one string table per locale, `strings/<tag>.json`,
mapping stable keys to text; game data, UI and plugins store the keys.

- `Strings` looks a key up in the active locale, its parent locales and then the default locale,
  and formats it. A missing key shows as the key and is logged once; nothing throws.
- `MessageFormat` is the ICU MessageFormat subset the text uses: `{name}`, `{n, number}`,
  `plural` (with `=N`, `offset:` and `#`), `select`, and ICU's apostrophe quoting.
- `PluralRules` holds the CLDR cardinal rules of the languages it covers; others count as `other`.
- `StringTableSource.assets` reads tables through a project `AssetSource`, one locale at a time,
  only when the locale is used.
- `StringLayer` orders sources: the game's tables over a plugin's defaults over Core's built-in text.
  `Strings.addSource` adds a layer; each locale's table is the layers merged key by key, and the
  locale comes before the layer, so a plugin's Korean beats the game's English for a Korean player.
- `InputLabels` names keys, mouse buttons, controller buttons and modifier chords in the player's
  language and `ShortcutStyle` (`Ctrl+Shift+K`, `Control+Shift+Cmd+K`). Its built-in names cover
  English, German, French and Spanish at `StringLayer.BuiltIn`; a game overrides them with the same
  `input.*` keys. It names what `core:input` describes: a `GamepadButton`, a `KeyChord` or
  `GamepadChord` (`chord`), any `BindingDescription` (`binding`) and a `ButtonAction` (`bindings`).
- `Strings.locale` is the active locale as a `StateFlow`; `setLocale` loads the new tables, then
  switches.
- `systemLocale()` reads the device language on every target.
- `LocaleFonts` and `Strings.loadFont` give a locale fallback fonts for scripts the UI font lacks,
  baked with only the chars its text uses, through `UiFonts.fallback` in `core:text`.

Compose UI reads the active table through `LocalStrings` and `stringResource` in
`compose:localization`, which keeps this module free of Compose. The guide is
`website/docs/guides/localization.md`; the engine showcase's `localization` sample
switches its text between English and Filipino.

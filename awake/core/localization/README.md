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
- `Strings.locale` is the active locale as a `StateFlow`; `setLocale` loads the new tables, then
  switches.
- `systemLocale()` reads the device language on every target.
- `LocaleFonts` and `Strings.loadFont` give a locale fallback fonts for scripts the UI font lacks,
  baked with only the chars its text uses, through `UiFonts.fallback` in `core:text`.

The guide is `website/docs/guides/localization.md`; the engine showcase's `localization` sample
switches its text between English and Filipino.

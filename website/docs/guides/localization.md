# Localization

<p class="awake-lede">Text in the player's language: one string table per locale, looked up by key, with placeholders and plural rules, switched while the game runs.</p>

<div class="awake-badges" markdown>
<span class="awake-badge">module: <code>awake:core:localization</code></span>
<span class="awake-badge awake-badge--ok">Desktop</span>
<span class="awake-badge awake-badge--ok">Android</span>
<span class="awake-badge awake-badge--ok">iOS</span>
<span class="awake-badge awake-badge--ok">Web</span>
</div>

Game data, UI and plugins store keys, such as `quest.harbor.title`, never the text itself. A
`Strings` turns a key into text in the active locale. Localization is code-only: there is no scene
document component for it, and AwakeKt Studio has no translations view yet.

## Write the string tables

A project keeps one JSON file per locale under `strings/`, named by its
[BCP 47](https://www.rfc-editor.org/info/bcp47) tag: `strings/en.json`, `strings/ko.json`,
`strings/pt-BR.json`. Each maps keys to text. An object groups keys: `menu.start` below is the
same key as a top-level `"menu.start"`.

=== "en.json"

    ```json title="strings/en.json"
    --8<-- "website/docs/snippets/localization/strings/en.json"
    ```

=== "ko.json"

    ```json title="strings/ko.json"
    --8<-- "website/docs/snippets/localization/strings/ko.json"
    ```

The tables are project files like scenes and assets, so they ship with the project and the
project-content plugin indexes them for the web by content hash.

## Look up text

Load the tables through the project's `AssetSource`, in the locale the player chose or the
system's:

```kotlin title="Kotlin"
--8<-- "awake/core/localization/src/desktopTest/kotlin/com/awakekt/awake/core/localization/LocalizationDocsSampleTest.kt:load"
```

Then ask for text by key:

```kotlin title="Kotlin"
--8<-- "awake/core/localization/src/desktopTest/kotlin/com/awakekt/awake/core/localization/LocalizationDocsSampleTest.kt:text"
```

A key is looked up in the active locale's table, then in its parents' (`pt-BR`, then `pt`), then in
the default locale's (`en` unless `Strings.load` is given another). A key that no table has shows
as the key itself and is logged once. Nothing here throws for missing or broken text.

## Placeholders, plurals and choices

Text follows ICU MessageFormat:

| Pattern | What it does |
| --- | --- |
| `{name}` | Puts in the argument `name`. `{name, number}` does the same for a number. |
| `{n, plural, one {# item} other {# items}}` | Picks a branch by the CLDR plural category of `n` in the text's language. `#` is the number. |
| `=0 {…}` | Inside `plural`: matches an exact value before the categories. |
| `offset:1` | Inside `plural`: subtracts from the number before the category and `#`. |
| `{g, select, female {…} male {…} other {…}}` | Picks a branch by the argument's text. |
| `''` | One apostrophe. |
| `'{…}'` | Literal braces: a `'` before `{`, `}` or, in a plural, `#` quotes up to the next `'`. |

```kotlin title="Kotlin"
--8<-- "awake/core/localization/src/desktopTest/kotlin/com/awakekt/awake/core/localization/LocalizationDocsSampleTest.kt:plural"
```

`plural` and `select` need an `other` branch. An argument's value is shown as it is and never
parsed, so a player name holding `{` is safe.

Languages count differently, and the categories a translation uses are its own language's:

| Language | Categories |
| --- | --- |
| English, German, Dutch, Swedish, Finnish, Estonian | `one` for 1, `other` |
| Spanish, Italian | `one`, `many` for millions, `other` |
| French, Portuguese (Brazil) | `one` for 0 and 1, `many` for millions, `other` |
| Filipino | `one`, except counts ending in 4, 6 or 9, which are `other` |
| Russian, Ukrainian | `one` (1, 21), `few` (2–4, 22–24), `many` (5–20, 25), `other` for fractions |
| Polish | `one` (1), `few` (2–4, 22–24), `many`, `other` for fractions |
| Arabic | `zero`, `one`, `two`, `few` (3–10), `many` (11–99), `other` |
| Japanese, Korean, Chinese, Thai, Vietnamese, Indonesian, Malay | `other` only |

A language not listed counts everything as `other`; pass `Strings.load` a `pluralRules` function
for it.

## Switch the locale

```kotlin title="Kotlin"
--8<-- "awake/core/localization/src/desktopTest/kotlin/com/awakekt/awake/core/localization/LocalizationDocsSampleTest.kt:switch"
```

`setLocale` loads the new tables first and then switches, so text is never half in one language.
`strings.locale` is a `StateFlow`. AwakeKt Compose composes every frame, so UI that reads
`strings.text(…)` while composing shows the new language in the next frame, with no restart.

`systemLocale()` reads the device's language: the JVM default on desktop and Android, the first
preferred language on iOS, and `navigator.language` in the browser.

## Fonts for other scripts

The built-in UI font draws Latin text. A locale whose script it lacks, such as Korean or Russian,
names fallback fonts in `strings/fonts.json`:

```json title="strings/fonts.json"
--8<-- "website/docs/snippets/localization/strings/fonts.json"
```

```kotlin title="Kotlin"
--8<-- "awake/core/localization/src/desktopTest/kotlin/com/awakekt/awake/core/localization/LocalizationDocsSampleTest.kt:fonts"
```

`loadFont` bakes each fallback font with only the chars the loaded tables use that the primary font
lacks, and returns one font, `UiFonts.fallback(primary, fallbacks)`, that draws each char from the
first face that has it. Give it to the UI, for example as Compose's `LocalFont`. Chars are read as
Unicode code points, so a font is found for a CJK Extension B ideograph or an emoji as for any other.

!!! warning "Bake fonts off the frame thread"
    Baking takes tens of milliseconds for a hundred glyphs and seconds for thousands. Call
    `loadFont` when the locale changes, keep the result, and do not call it per frame. Text from
    outside the tables, such as a player's name, can hold chars no table has: pass them as
    `extraChars`.

!!! warning "No shaping"
    Text draws one grapheme cluster at a time, so the caret, Backspace and a cut line never split an
    emoji or a letter from its marks, but nothing is shaped: Thai's combining marks and Arabic's
    joined letters draw as separate glyphs, emoji draw as the font's missing-glyph box, and
    right-to-left layout is not supported yet.

## How it works

Tables load per locale, when that locale is used: a project with many languages reads and parses
only the active locale's chain and the default's, and drops the others when it switches. A message
is parsed the first time it is shown and kept; text with no `{` or `'` is shown as it is.

## Debugging

Every problem is logged once per locale through `Log`, with the tag `Strings`:

| Warning | Means |
| --- | --- |
| `No text for "<key>"` | No table in the chain has the key. The key is shown. |
| `No string table for <locale>` | The locale and its parents have no table; the default is shown. |
| `Text "<key>" in <locale> is not a valid message` | The text breaks the syntax. The key is shown. |
| `Text "<key>" in <locale>: argument "<name>" is missing` | A placeholder had no value. `{name}` is shown. |

The engine showcase's **Localization** sample switches its debug panel between English and
Filipino: `./gradlew :samples:engine-showcase:run -Pawake.showcase=localization`.

## See also

- [Game UI](game-ui.md), for the text that shows these strings.
- [Modules](../reference/modules.md), for the `awake-core-localization` coordinate.

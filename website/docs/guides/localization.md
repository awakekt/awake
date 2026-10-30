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
`Strings` turns a key into text in the active locale. A scene's canvas text can show a key with no
code, and a played project loads its tables itself; see
[Show a key in a scene](#show-a-key-in-a-scene). AwakeKt Studio has no translations view yet.

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
| `{name}` | Puts in the argument `name` as it is: a number is not grouped. |
| `{n, number}` | Writes a number the locale's way: `1,234.5`, `1.234,5`, `1 234,5`. |
| `{n, number, integer}` | Rounds to a whole number: `1,235`. |
| `{n, number, percent}` | Times 100, with the locale's percent sign: `45%`, `45 %`, `%45`. |
| `{n, number, compact}` | Short, for a HUD: `1.2K`, `1,2 Mio.`, `1.2万`. ICU's `::compact-short` also works. |
| `{n, plural, one {# item} other {# items}}` | Picks a branch by the CLDR plural category of `n` in the text's language. `#` is the number, written the locale's way. |
| `{n, selectordinal, one {#st} two {#nd} few {#rd} other {#th}}` | Picks a branch by the CLDR ordinal category: 1st, 2nd, 3rd, 4th, 11th, 21st. |
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

Ordinals have rules of their own. English uses `one` (1st, 21st), `two` (2nd, 22nd), `few` (3rd,
23rd) and `other` (4th, 11th–13th). Swedish uses `one` for 1st and 2nd, Italian `many` for 8, 11, 80
and 800, French, Filipino, Malay and Vietnamese `one` for 1 only, and Ukrainian `few` for 3rd. The
other languages above use `other` for every number. `Strings.load` takes an `ordinalRules` function
for a language not covered.

## Numbers

`{n, number}`, its styles and a plural's `#` write numbers the way the text's language does:

| Locale | `{n, number}` of 12345.6 | `percent` of 0.45 | `compact` of 1234567 |
| --- | --- | --- | --- |
| English | `12,345.6` | `45%` | `1.2M` |
| German | `12.345,6` | `45 %` | `1,2 Mio.` |
| French | `12 345,6` | `45 %` | `1,2 M` |
| Spanish | `12.345,6`, but `1234` | `45 %` | `1,2 M` |
| Japanese | `12,345.6` | `45%` | `123万` |
| Turkish | `12.345,6` | `%45` | `1,2 Mn` |

Spanish, Italian, Polish, Estonian and European Portuguese group only from five digits, so 1234 has
no separator. Numbers round half to even, keep up to three fraction digits, and use Latin digits.
`compact` keeps two significant digits below ten of a unit (`1.2K`) and whole numbers above (`12K`).
A plain `{n}` stays as it is, so a year or an ID is never written `2,026`.

The data is Unicode CLDR's, generated by `./gradlew :awake:core:localization:generateCldrNumberData`
for every language the plural rules cover. Another language uses CLDR's root format, which writes `12,345.6`. Code that
writes a number outside a message uses `NumberFormat.forLocale(strings.locale.value).format(n, style)`.
French and Russian group with a no-break space, which the built-in font draws as a space.

## Switch the locale

```kotlin title="Kotlin"
--8<-- "awake/core/localization/src/desktopTest/kotlin/com/awakekt/awake/core/localization/LocalizationDocsSampleTest.kt:switch"
```

`setLocale` loads the new tables first and then switches, so text is never half in one language.
`strings.locale` is a `StateFlow`. AwakeKt Compose composes every frame, so UI that reads
`strings.text(…)` while composing shows the new language in the next frame, with no restart.

`systemLocale()` reads the device's language: the JVM default on desktop and Android, the first
preferred language on iOS, and `navigator.language` in the browser.

## Text a plugin ships

A plugin or library that shows text ships its own defaults, so it works before a game translates a
word, and the game still has the last word. It adds its tables as a layer:

```kotlin title="Kotlin"
--8<-- "awake/core/localization/src/desktopTest/kotlin/com/awakekt/awake/core/localization/LocalizationDocsSampleTest.kt:layers"
```

There are three layers, `StringLayer.Game`, `Plugin` and `BuiltIn`. The game's own tables, the ones
given to `Strings.load`, win over a plugin's, and a plugin's win over the text Core ships. Within a
layer, a source added later wins. Each locale's table is the layers merged key by key, so the game
overrides one plugin key in `strings/en.json` and keeps the rest.

The locale comes before the layer. A Korean player sees the plugin's Korean text before the game's
English, because text in their language beats text in the wrong one; the game's English wins over
the plugin's English. A game that renames a plugin's term therefore translates it into every locale
it ships. `removeSource` takes a layer away again.

## Show text in Compose UI

`compose:localization` gives Compose UI the active table without making `core:localization` depend
on Compose:

```kotlin title="Kotlin"
context(_: Composer)
fun Hud() {
    ProvideStrings(strings, labels) {
        Text(stringResource("hud.items", "n" to bag.count))
    }
}
```

`LocalStrings` holds the `Strings`, and `stringResource(key, args)` reads it. AwakeKt Compose
composes every frame, so the text shows a new locale on the frame after `setLocale` returns. A
missing key, or no `LocalStrings` at all, shows the key and is logged once.

## Name keys and buttons

Button prompts and hotkey labels name keys, mouse buttons and controller buttons in the player's
language and in their platform's style:

```kotlin title="Kotlin"
--8<-- "awake/core/localization/src/desktopTest/kotlin/com/awakekt/awake/core/localization/LocalizationDocsSampleTest.kt:input"
```

`InputLabels.load` adds Core's built-in names, English, German, French and Spanish, at
`StringLayer.BuiltIn`. `ShortcutStyle.system()` picks the style: Windows writes `Win+Ctrl+Alt+Shift`,
Linux names Meta `Super`, and Apple platforms write `Control+Option+Shift+Cmd`, with `Option` for Alt.
`ShortcutStyle.primaryModifier` is Cmd on Apple platforms and Ctrl elsewhere, for an editor-style
`KeyChord`. `ControllerLayout` picks what controller buttons are printed with: `Letters` (A at the
bottom), `SwappedLetters` (B at the bottom) or `Shapes` (Cross at the bottom).

The labels name what [input actions](input.md) describe. `chord(chord)` names a `KeyChord` or a
`GamepadChord`, `binding(description)` any `BindingDescription`, and `bindings(action)` every way to
trigger a `ButtonAction`, keys then pointer buttons then chords: `E / Right click / Shift+E`. Modifier
keys are written in the platform's order, controller modifiers in `GamepadButton` order: `LB+A`.

A game overrides any name, or adds a language, with the same keys in its tables:

| Key | Names |
| --- | --- |
| `input.key.<Key>` | A key, such as `input.key.Space`. Letters, digits and F-keys default to themselves. |
| `input.pointer.<PointerButton>` | A mouse button, such as `input.pointer.Primary`. |
| `input.gamepad.<GamepadButton>` | A controller button by position, such as `input.gamepad.South`. |
| `<key>.windows`, `.linux`, `.apple` | A key's or mouse button's name on one platform, such as `input.key.Meta.apple`. |
| `<key>.letters`, `.swapped`, `.shapes` | A controller button's name in one layout. |
| `input.chord.separator` | Joins a chord: `+`. Set `input.chord.separator.apple` to `""` and the modifiers to `⌃⌥⇧⌘` for Apple's symbols, with a font that has them. |
| `input.bindings.separator` | Joins an action's bindings: ` / `. |

A platform or layout form wins over the plain key in the same locale, and the player's locale wins
over the default's.

## Show a key in a scene

A `canvas_element` of kind `Text` or `Button` shows a key's text with `textKey` in place of `text`.
`textArgs` gives its placeholders values, and a `bind`'s `textArgs` reads one from a component
field the game sets, such as a count:

```json title="scenes/main.scene.json"
{ "component": "canvas_element", "textKey": "hud.items", "textArgs": { "name": "Harbor Town" },
  "bind": { "node": "Player", "textArgs": { "count": "inventory.count" } } }
```

`loadProject` reads `strings/` through the project's files, the way it reads scenes and assets, so
the tables are indexed by content hash on the web like any other file. It reads the device's
locale's table and `strings/en.json`, the default; another locale's table is read only when the game
switches to it. `LoadedProject.strings` is that `Strings`, and every scene the project plays shows
its keyed text from it. A settings screen switches language through it:

```kotlin title="Kotlin"
project.strings?.setLocale(LocaleId.of("ko"))
```

Keyed text changes on the next frame; the scene keeps playing. A project with no `strings/en.json`
has no strings, and its keyed elements show their keys. A host that draws the canvas itself passes
`SceneCanvas(world, strings = …)`. The fields are in [Game UI](game-ui.md#show-text-in-the-players-language).

### Text before the project loads

A game that shows text before its project has loaded, such as a connection screen saying why a
server refused the session, makes its own `Strings` first and hands it to `loadProject`, so the game
and the project share one instance:

```kotlin title="Kotlin"
val strings = Strings.load(StringTableSource.assets(appResources), locale = settings.locale ?: systemLocale())
showConnecting(strings.text("connect.waiting"))

val project = loadProject(projectFiles, strings = strings, physicsWorld = ::createJoltPhysicsWorld)
// project.strings === strings
```

- **One instance.** The project's `strings/` tables are added to it at `StringLayer.Game`, and
  `LoadedProject.strings` is that same `Strings`. Text the game read before loading and the project's
  keyed text both come from it.
- **The game's locale is kept.** The project's tables are read for the locale the game chose, not the
  device's, and one `setLocale` changes the game's text and the project's together.
- **The project's keys win.** Its tables are added after the game's own, so where both have a key in
  the same locale the project's text is shown. The locale still comes first: the game's Korean for a
  key beats the project's English for a Korean player.
- **A failed load changes nothing.** The tables are added only once the rest of the project has
  loaded.
- **Each load adds the tables again.** A game that loads a project again into the same instance, or
  one project after another, keeps every load's tables, the newest winning.

Without `strings`, the project gets a `Strings` of its own, as above.

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

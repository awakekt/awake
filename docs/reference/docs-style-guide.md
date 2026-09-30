# AwakeKt docs style guide

How pages under `website/docs/` are written. The first page on this template is
`website/docs/guides/lights-and-shadows.md`; copy its shape.

## Sections of the site

| Section | Path | What a page is |
| --- | --- | --- |
| Get started | `get-started/` | A tutorial. Linear, one outcome, ends with something that runs. |
| Guides | `guides/` | One task or feature: how to use it, in every form it can be written. |
| Reference | `reference/` | Lookup tables: every field, id, module, default. No narrative. |
| API | `/api/` | Dokka, generated. Never hand-written. |

## Terms

Use exactly these words. Do not introduce synonyms.

| Concept | Term | Not |
| --- | --- | --- |
| A `*.scene.json` file, loaded by the scene runtime and saved by AwakeKt Studio | **scene document** | scene file, scene.json (as a noun), authored scene, scene JSON |
| Kotlin `world.scene { entity("…") { … } }` / `app { scene("…") { … } }` | **scene DSL** | Kotlin DSL, scene authoring DSL, declarative API |
| `World`, entities, components, `System` | **ECS** | entity system, world API |
| The `awake:scene:runtime` module | **scene runtime** | only for that module, not the whole scene family |
| The products | **AwakeKt Engine**, **AwakeKt Studio**; the project is **AwakeKt** | Awake Engine, Awake Studio (package names like `com.awakekt.awake` are fine) |
| A component inside a scene document | its **component id**, e.g. `light`, `mesh_renderer` | type name, class name |

A scene document and the scene DSL are two ways to describe the same ECS content. Say so wherever
both appear, and never present them as separate engines.

## Guide page shape

1. `# Title`: a noun phrase ("Lights and shadows", "Character controller").
2. Lede: `<p class="awake-lede">…</p>`, one or two sentences on what it is for.
3. Badges: `<div class="awake-badges" markdown>` with `awake-badge` spans: the component id(s), then
   backends that support it (`awake-badge--ok`), then platforms. Only claim what the code supports.
4. Figures (optional): `<div class="awake-figures" markdown>` with `<figure markdown>` +
   `<figcaption>`. Images live in `website/docs/assets/guides/<page>/`. Use engine renders, never
   mock-ups.
5. `## <Do the thing>`: the task, with content tabs in this order:

   ```markdown
   === "Scene document"

       ```json title="name.scene.json"
       --8<-- "website/docs/snippets/<area>/name.scene.json"
       ```

   === "Scene DSL"

       ```kotlin title="Kotlin"
       --8<-- "path/to/SomeDocsSampleTest.kt:region"
       ```

   === "Studio"

       Numbered steps using only labels that exist in AwakeKt Studio.
   ```

   Omit a tab only when that form genuinely cannot express the feature, and say why in one line.
   Code-only features (systems, render plans, lifecycle) use a single Kotlin block instead of tabs.
6. `## Properties`: a table `Property | Type | Default | What it does`, taken from the code.
7. `## How it works`: short explanation of the model.
8. Callouts: `!!! tip "…"` and `!!! warning "…"` for real pitfalls found in code or tests.
9. `## Debugging` (when a debug view, log, or diagnostic exists).
10. `## See also`: relative links to related guides and reference pages.

Headings are sentence case. No emoji. Keep sentences short and concrete.

## Samples

Every code sample is included with `--8<--`; none is typed into the page.

- **Kotlin:** a region in a test that compiles and runs. Mark it with `// --8<-- [start:name]` and
  `// --8<-- [end:name]`, and include it as `"path/File.kt:name"`. Name docs sample tests
  `…DocsSampleTest` and assert the sample does what the page says.
- **Scene documents:** a file under `website/docs/snippets/<area>/`. A test decodes it with
  `SceneLoader.decode`, instantiates it after `DefaultSceneComponentResolvers.install()`, and
  checks the result. When a page shows the same thing in both forms, the test asserts both build
  equal components.
- A test in a module's `desktopTest` reads snippet files relative to the module directory
  (`File("../../../website/docs/snippets")` from `awake/<group>/<module>`).
- Use the canonical snake_case component ids in new scene documents (`mesh_renderer`, not
  `meshRenderer`).
- Code blocks always name a language; files get `title="file.name"`.
- Versions come from macros: `{{ awake_version }}`, `{{ awake_vulkan_version }}`. Never hard-code a
  release number.

## Reference pages

Tables only, one row per item, every value taken from code. Link each row to the guide that
explains it.

## Never

- Name a third-party commercial game, its places, characters, or formats. Use neutral fixture
  names ("Harbor Town").
- Describe an API, field, label, or behaviour you have not found in code or seen run.
- Copy upstream Jetpack Compose or other framework docs: AwakeKt Compose is its own engine.

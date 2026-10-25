# Content bundles

<p class="awake-lede">Pack a published project's files into a few archives named by their SHA-256, so a full load is one request per bundle instead of one per file, and the index needn't list a browsable file tree.</p>

<div class="awake-badges" markdown>
<span class="awake-badge">module: <code>com.awakekt.awake:project</code></span>
<span class="awake-badge">CLI: <code>awake bundle</code></span>
<span class="awake-badge">Desktop · Android · iOS · Web</span>
</div>

A published project is read through `IndexedAssetSource`, which checks every file against the
project index. With loose files, a scene of a thousand models, textures and sounds is a thousand
requests, and each one pays for a round trip, a CDN or edge function invocation, and any read-token
check. A content bundle holds many files in one archive. The publish step writes the bundles and a
bundled index; the game opens that index exactly as it opens a loose one.

## Write bundles in the publish step

Start from the loose index the build makes (`./gradlew generateProjectIndex` writes
`build/project.index.json`). From the command line:

```bash
awake bundle build/project.index.json --output build/published
```

That writes `build/published/bundles/<sha256>.awkb` and the bundled
`build/published/project.index.json`. Upload the folder as it is. Options: `--group folder|one`,
`--depth <n>`, `--target-mb <n>` (16 by default), and `--opaque-ids --salt <text>`.

From Kotlin, for a publish step of your own, `ContentBundleWriter` does the same. It reads each
file once, checks it against the loose index, and hands you one bundle at a time:

```kotlin title="Kotlin"
--8<-- "awake/project/src/commonTest/kotlin/com/awakekt/awake/project/ContentBundlesDocsSampleTest.kt:write"
```

The output is deterministic: the same files give byte-identical bundles, and a bundle's URL is its
SHA-256, so a second publish uploads only the bundles whose files changed.

## Read them in the game

Nothing changes for the game. `IndexedAssetSource.open` reads a bundled index as it reads a loose
one, with a plain fetch or a [`TokenAuthorizedFetch`](protected-content.md):

```kotlin title="Kotlin"
--8<-- "awake/project/src/commonTest/kotlin/com/awakekt/awake/project/ContentBundlesDocsSampleTest.kt:open"
```

The first read of any member fetches its whole bundle once, checks the bundle and every member
against their SHA-256s, and stores each member in the `ContentCache` by its own SHA-256. The rest of
that bundle's files then read with no request. `prefetch` downloads whole bundles, one per bundle,
and skips a bundle whose files are already cached.

## Choose the groups

A bundle should hold files that load together. A scene that needs one texture from a 32 MiB bundle
downloads all 32 MiB.

| Grouping | When | How |
| --- | --- | --- |
| By top folder | The default. Folders such as `scenes/`, `assets/` and `ui/` load at different times. | `ContentBundleGrouping.byFolder()`, `--group folder` |
| By deeper folder | A folder per level, area or character, such as `assets/harbor/`, `assets/pier/`. | `byFolder(depth = 2)`, `--depth 2` |
| By scene | Each scene's own files in its own bundles, shared files apart. | Your own `ContentBundleGrouping`; see below |
| One group | A small project that loads everything up front. | `ContentBundleGrouping.oneGroup()`, `--group one` |

Core doesn't parse scenes to find which files each uses; your publish step knows. Name a group per
scene and fall back to folders for the rest:

```kotlin title="Kotlin"
--8<-- "awake/project/src/commonTest/kotlin/com/awakekt/awake/project/ContentBundlesDocsSampleTest.kt:grouping"
```

Keep the files a scene needs first (the manifest, the entry scene, its UI) in small bundles of their
own, so the first frame doesn't wait for the largest bundle. `byFolder` already puts the files at the
project root, `awake.project.json` among them, in a bundle of their own.

## Choose a size

Each group is split into bundles filled up to the size target, in path order. A file larger than the
target gets a bundle of its own.

| Target | Suits |
| --- | --- |
| 4 MiB | Slow or metered links, and browsers on low-memory devices: little over-fetch, and a failed download costs little to repeat. |
| 16 MiB, the default | Most hosts: a large project in tens of requests, with modest over-fetch. |
| 32 MiB | Fast links and per-request costs that dominate, such as a token-checking edge function on every request. |

Weigh these:

- **Requests.** Every request through an authorizing edge costs an invocation and counts against a
  limit. Bigger bundles mean fewer.
- **Over-fetch.** A bundle downloads whole. Bigger bundles fetch more that a scene doesn't use, unless
  the groups match what loads together.
- **Re-uploads.** Changing a file changes its bundle and can move the split points after it in its
  group, so later bundles of that group change too. Other groups are untouched. Smaller groups keep a
  change local.
- **Memory.** A bundle is held whole while it is checked. With a `ContentCache`, members are then kept
  there; with none, up to 64 MiB of not-yet-read members wait in memory.
- **Host limits.** Some CDNs and edge platforms cap a cached object or a response body. Keep the target
  under the cap.

## Hide the file list

With opaque ids, the index and the bundles carry a salted hash of each path instead of the path:

```kotlin title="Kotlin"
--8<-- "awake/project/src/commonTest/kotlin/com/awakekt/awake/project/ContentBundlesDocsSampleTest.kt:opaque"
```

The game still reads by path: `IndexedAssetSource` hashes the path it is asked for with the salt the
index publishes, and looks that id up.

!!! warning "Opaque ids are not encryption"
    The salt is in the index, so anyone can check whether a path they guess is in the project. Ids
    stop a reader from listing the files; they don't hide the files' contents.

## Read one member alone

A host that serves HTTP range requests can read one member without its bundle. Pass a range fetch;
with `TokenAuthorizedFetch`, use `IndexedAssetSource.open(fetch = tokenFetch, rangeReads = true)`,
which sends `Range: bytes=<first>-<last>` alongside the token.

```kotlin title="Kotlin"
--8<-- "awake/project/src/commonTest/kotlin/com/awakekt/awake/project/ContentBundlesDocsSampleTest.kt:range"
```

Each `read` of a member then costs one request for just its bytes, while `prefetch` still downloads
whole bundles. Use it for content streamed one file at a time from large bundles, and prefetch what a
scene needs up front. A server that ignores the range answers the whole bundle, and the member is cut
from it.

## Properties

| Property | Type | Default | What it does |
| --- | --- | --- | --- |
| `grouping` | `ContentBundleGrouping` | `byFolder(1)` | Which group each path is in; no bundle mixes groups. |
| `targetBundleBytes` | `Long` | 16 MiB | The size a bundle fills to before the next one starts. |
| `pathIds` | `ProjectPathIds?` | `null` | With a salt, ids replace paths in the index and bundles. |
| `bundleUrlPrefix` | `String` | `bundles` | The folder bundle URLs start with, relative to the index. |

## How it works

**The bundle format.** A bundle is a header, a table of contents, then the members' bytes. All
integers are big-endian.

| Part | Bytes | Holds |
| --- | --- | --- |
| Magic | 4 | `AWKB` |
| Version | 2 | `1` |
| Reserved | 2 | `0` |
| Member count | 4 | How many table records follow |
| Table size | 4 | The table's size in bytes |
| Table record | 2 + key + 8 + 8 + 32 | Key length, key (a path or an opaque id), offset from the bundle's first byte, size, SHA-256; sorted by key |
| Data | the rest | Each distinct content once, in table order, no padding |

The header and table come first, so a reader with range requests can read the table without the
members. There are no timestamps, so the same members always encode to the same bytes.

**The index.** `formatVersion` 2 lists loose files, each with a `url`. `formatVersion` 3 adds a
`bundles` list (`sha256`, `sizeBytes`, `url`), and lets an entry name a `bundle` and an `offset`
instead of a `url`, and carry an `id` instead of a `path` when `pathIds` is set. An engine refuses an
index version it doesn't know, so an older engine fails clearly on a bundled index rather than
misreading it. A loose index reads exactly as it always has, and the two can share one cache: members
are cached by their own SHA-256, as loose files are.

**Checks.** A bundle's size and SHA-256 must match the index, and so must each member's. A bundle
that fails is rejected whole, nothing from it is cached, and the error names every damaged member's
path (or id), or the header or table when no member is damaged.

## See also

- [Protected content](protected-content.md)
- [Run in the browser](../get-started/run-in-the-browser.md)
- [Modules](../reference/modules.md)

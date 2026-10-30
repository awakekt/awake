# Content bundles

<p class="awake-lede">Pack a published project's files into a few archives named by their SHA-256, so a full load is one request per bundle instead of one per file, and the index needn't list a browsable file tree.</p>

<div class="awake-badges" markdown>
<span class="awake-badge">module: <code>com.awakekt.awake:project</code></span>
<span class="awake-badge">Gradle: <code>generateContentBundles</code></span>
<span class="awake-badge">CLI: <code>awake bundle</code></span>
<span class="awake-badge">Desktop · Android · iOS · Web</span>
</div>

A published project is read through `IndexedAssetSource`, which checks every file against the
project index. With loose files, a scene of a thousand models, textures and sounds is a thousand
requests, and each one pays for a round trip, a CDN or edge function invocation, and any read-token
check. A content bundle holds many files in one archive. The publish step writes the bundles and a
bundled index; the game opens that index exactly as it opens a loose one.

## Write bundles in the publish step

In a Gradle project with `com.awakekt.awake.plugin.project-content` applied, run:

```bash
./gradlew generateContentBundles
```

That runs `generateProjectIndex`, packs its files with `com.awakekt.awake:project`'s writer in an
isolated Gradle worker, and writes `build/published/bundles/<sha256>.awkb` plus the bundled
`build/published/project.index.json`. Upload `build/published` as it is.

The task deletes bundles in `build/published` that the new index no longer lists, so the folder holds
one release. Pointed at a folder outside the build, such as a publish folder that still serves the
previous release's bundles to players mid-update, it keeps them unless `pruneStaleBundles` is set.

From the command line, starting from a loose index (`./gradlew generateProjectIndex` writes
`build/project.index.json`), `awake bundle` writes the exact same files:

```bash
awake bundle build/project.index.json --output build/published
```

Options: `--group folder|one`, `--depth <n>`, `--target-mb <n>` (16 by default),
`--opaque-ids --salt <text>`, and `--prune`, which deletes `.awkb` files under the output folder that
the new index doesn't list. Without it, the command keeps them.

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

The first read of any member fetches its whole bundle once, checks the bundle's SHA-256 against the
index and each member against the bundle's table of contents, and stores each member in the
`ContentCache` by its own SHA-256, hashing each one as it goes in, since the cache outlives the
index. The rest of that bundle's files then read with no request. `prefetch` downloads whole bundles, one per bundle,
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

Each group larger than the size target is split at deterministic content-hash peaks bounded by the
target, in path order. A file larger than the target gets a bundle of its own.

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
- **Re-uploads.** Because split points within a group follow content-defined hash boundaries, adding,
  editing or removing one file typically re-packs its own bundle (two when that bundle splits) and
  leaves later bundles of that group, and every other group, untouched. That is likely rather than
  certain: a run of files longer than the target with no hash peak in it is filled greedily, so a
  change inside it can move the later bundles up to its end. Smaller targets still make each
  re-uploaded bundle smaller.
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
from it. Both ready-made transports, `KtorContentTransport` and `BrowserContentTransport`, send the
`Range` header; from a browser to another origin, the content server must allow it, as
[Protected content](protected-content.md#the-browser-and-cors) lists. A range request answered 429,
or 503 with `Retry-After`, waits and is sent again like any other.

## Properties

`ContentBundleWriter`:

| Property | Type | Default | What it does |
| --- | --- | --- | --- |
| `grouping` | `ContentBundleGrouping` | `byFolder(1)` | Which group each path is in; no bundle mixes groups. |
| `targetBundleBytes` | `Long` | 16 MiB | Upper size target per bundle; larger groups split at content-defined boundaries within it. |
| `pathIds` | `ProjectPathIds?` | `null` | With a salt, ids replace paths in the index and bundles. |
| `bundleUrlPrefix` | `String` | `bundles` | The folder bundle URLs start with, relative to the index. |

`projectContent` Gradle extension (`generateContentBundles`):

| Property | Type | Default | What it does |
| --- | --- | --- | --- |
| `bundlesOutputDirectory` | `DirectoryProperty` | `build/published` | Folder where `bundles/<sha256>.awkb` and the bundled `project.index.json` are written. |
| `bundleGrouping` | `Property<String>` | `"folder"` | `"folder"` (`byFolder`) or `"one"` (`oneGroup`). |
| `bundleFolderDepth` | `Property<Int>` | `1` | Leading folder count when `bundleGrouping` is `"folder"`. |
| `targetBundleBytes` | `Property<Long>` | 16 MiB | Upper size target per bundle. |
| `bundleOpaqueIds` | `Property<Boolean>` | `false` | Whether to replace paths in the index and bundles with salted opaque ids. |
| `bundlePathSalt` | `Property<String>` | unset | Salt required when `bundleOpaqueIds` is `true`. |
| `bundleUrlPrefix` | `Property<String>` | `"bundles"` | Folder prefix for bundle URLs under `bundlesOutputDirectory`. |
| `pruneStaleBundles` | `Property<Boolean>` | `true` inside the build folder, else `false` | Whether `.awkb` files under `bundlesOutputDirectory` that the new index doesn't list are deleted. |

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

**Checks.** A whole bundle's size and SHA-256 are checked once against the index; when they match
and every member matches its record in the bundle's table of contents, member slices read with no
`ContentCache` are trusted without hashing their bytes a second time. Members kept in a
`ContentCache` are each hashed before they're stored, because the cache serves later releases by
SHA-256 and checks a hit by size alone. Single-member range reads still check each member's size
and SHA-256 directly. A bundle that fails its check is rejected whole, nothing from it is cached,
and its members are hashed to name every damaged member's path (or id), or the header or table when
no member is damaged.

## See also

- [Protected content](protected-content.md)
- [Encrypted content](encrypted-content.md)
- [Run in the browser](../get-started/run-in-the-browser.md)
- [Modules](../reference/modules.md)

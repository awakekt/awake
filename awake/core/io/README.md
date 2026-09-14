# Awake Core IO

This module owns the reusable, asynchronous, root-relative file and byte-source contracts used
by engine consumers such as Awake Studio. It does not decide project layout, picker UX, plugin
installation, or cloud persistence.

Platform adapters provide the same rooted semantics on every shipped target:

- Desktop uses `java.nio.file`, temporary-file atomic publication, and native watch events.
- Android uses app/document storage, temporary-file publication, and a deterministic polling watch.
- iOS uses `NSFileManager`, temporary-file publication, and ordered adapter-local watch events.
- Wasm uses the transactional in-memory engine with a `localStorage`-backed virtual filesystem when
  browser storage is available. A browser-selected File System Access handle remains an explicit
  integration concern because the browser cannot grant one implicitly.

The Wasm storage key is namespaced by the factory root argument. This makes Studio sessions survive
reloads without coupling Core to Studio's project picker or cloud policy. Storage quota failures
are returned as structured `FileSystemError.IoFailure` results.

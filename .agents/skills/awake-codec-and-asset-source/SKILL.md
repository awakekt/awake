---
name: awake-codec-and-asset-source
description: Keep Awake codecs pure and synchronous while resolving external asset bytes through injected asynchronous sources.
---

# Awake codecs and asset sources

Codecs accept bytes and return typed values; they do not select directories, download files, install plugins, or persist documents. Use `AssetSource` for asynchronous `.bin`, image, resource, and project reads, resolve sidecars relative to the containing asset, then pass fetched bytes to a synchronous parser. Use the shared bounds-checked `BinaryReader` for binary formats. Preserve existing serialized JSON shapes and add round-trip and malformed-input tests before removing a product-specific reader.

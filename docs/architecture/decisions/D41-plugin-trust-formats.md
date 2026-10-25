# D41: plugin trust formats live in Core

Status: accepted (2026-10-25). Amends [D35](D35-editor-boundary.md).

## Decision

How an Awake plugin is described, packaged and trusted is one public Core module,
`:awake:plugin-archive` (`com.awakekt.awake:plugin-archive`, package `com.awakekt.awake.pluginarchive`):

- `PluginManifest` and `PluginDependency` (`plugin.json`), moved from `:awake:editor:contract`.
- The `.awakeplugin` zip: `PluginArchive`, `PluginArchiveReader`, its limits (4096 entries, 64 MiB
  unpacked) and safe entry paths.
- Signature formats and checks: `PluginArchiveDigest` and `PluginArchiveVerifier` (moved),
  `AwakeRootSignature` and its signature domains, `PublisherKeyList` / `SignedPublisherKeyList`,
  `PluginRevocationList` / `SignedPluginRevocationList`, and the `PluginTrust` decision with its
  `PluginVerificationResult`.
- `PluginRuntimeModule` and `AwakePluginsLock`, the `plugins.lock.json` a project keeps.

D35 put "signatures" on the Studio side. This record narrows that line: the *formats* and their
*verification* are Core; what a host does with the result stays with the host. Installing plugins,
resolving dependencies, preflight, the catalogue, which root keys to trust and when to refresh the
lists, and any product licence format are host code and never enter Core.

## Why

Three parties read and write the same bytes: Core's plugin template signs archives, the editor
checks them before loading anything, and the cloud service countersigns versions and publishes
revocation lists. With the formats split between the editor contract and private code, the three
drifted, and private package publishing was not reliable enough to share them. The formats are
public by design: verification needs only public keys, and signing keys stay in the signing
service's secret store, so nothing is lost by publishing them under Apache-2.0.

The module is not under `:awake:editor:*` because a played project needs `plugins.lock.json` and
`PluginRuntimeModule` without any editor, and not under `com.awakekt.awake.plugin.*` (the Gradle
plugin IDs) or `com.awakekt.awake.plugins.*` (the gameplay plugin libraries, which depend on Core).

## Boundary record (framework-boundary rule 5)

| | |
|---|---|
| Consumers | Core's `templates/plugin` (signing), the editor host (checking before load), the cloud service (countersigning and revocation lists). |
| Missing API | Verification of root-signed documents and of whole archives on every target, including web and iOS, where the downstream checks returned `false`. |
| Contract owner | Core, `:awake:plugin-archive`. |
| Excluded policy | Install, dependency resolution, preflight, catalogue, trusted-key selection, refresh cadence, licences. |
| Dependency direction | `:awake:editor:contract` → `:awake:plugin-archive`; the module depends on no editor, UI, scene or GPU module. |
| Validation | Common tests on desktop, Android, iOS and wasmJs: openssl-made golden signatures, keys generated at test time, zip limit and path cases. |

## Consequences

- Signature and digest checks go through cryptography-kotlin and each platform's own provider
  (JDK, Apple Security/CommonCrypto, WebCrypto), never hand-written crypto. WebCrypto is
  asynchronous, so every check is `suspend`.
- `:awake:editor:contract` keeps deprecated aliases and blocking forwards for one release.

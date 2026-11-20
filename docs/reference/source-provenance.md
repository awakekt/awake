# Source provenance

Awake source is original unless an entry in `source-provenance.json` records an external source.
This record makes reviewable evidence of permission; it is not proof that unrecorded code is original.

## Rule

Prefer an upstream dependency, specification, or clean-room reimplementation. Do not copy an
implementation merely because its API or visual result is useful. When a file or bounded snippet
must be adapted, keep the upstream copyright and SPDX license, confirm compatibility with
Apache-2.0, and add a registry entry before committing.

For a whole file, preserve its `SPDX-FileCopyrightText` and `SPDX-License-Identifier` header and
record `scope: "file"`. For a bounded block, wrap it in `SPDX-SnippetBegin` and
`SPDX-SnippetEnd`, use `SPDX-SnippetCopyrightText`, and record `scope: "snippet"`.

## Registry entry

```json
{
  "path": "awake/example/AdaptedThing.kt",
  "scope": "snippet",
  "upstream": "https://example.org/project/blob/<commit>/Thing.kt",
  "revision": "<immutable commit or release>",
  "copyright": "Example Authors",
  "license": "Apache-2.0",
  "reason": "Why adaptation is necessary and why an upstream dependency is unsuitable"
}
```

The pre-commit provenance gate requires a matching path and scope. Review checks the other fields:
the URL and revision must be immutable, the license must be compatible, and the reason must be
specific. Missing licensing information means the code cannot be copied.

### Origin unrecorded

Code whose external source was never recorded, and which the owner can't trace, still gets an entry,
so it is visible rather than presumed original. It uses the same fields, with `upstream`, `revision`,
`copyright` and `license` set to `"unrecorded"`, and a `reason` that states:
- the pull request that added it;
- what is known of how it differs from the published algorithm it resembles;
- which values it shares with a published source;
- that it is not certified clean-room;
- what replaces it, and when.

The code itself carries no `SPDX-Snippet` markers, since no copyright holder is known, but a comment
that points to its entry. Its documentation names it for what it does, never as an implementation
of a published version. The FXAA-style stage in `PostProcessingShaders.kt` is the first such entry
(#1791).

## Audits

`reuse lint` checks declared per-file copyright/license metadata. ScanCode reports detected
copyright, license, package, and dependency evidence. PMD CPD reports internal copy/paste
similarity. None can determine that all code is original; treat every result as a review lead.

## Brand assets

The provenance registry records externally sourced code and assets; it does not establish the
ownership of project-authored names or marks. The [AwakeKt brand system](brand-system.md) records the
Ember Signal source master, its dated issue and merge history, and its approved use. The separate
[trademark notice](../../TRADEMARKS.md) governs brand use.

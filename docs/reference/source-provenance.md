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

## Audits

`reuse lint` checks declared per-file copyright/license metadata. ScanCode reports detected
copyright, license, package, and dependency evidence. PMD CPD reports internal copy/paste
similarity. None can determine that all code is original; treat every result as a review lead.

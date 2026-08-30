# REUSE baseline — 2026-08-25

`reuse 6.2.0 lint` establishes this repository's starting point for making REUSE a required CI
gate. The result is deliberately recorded rather than hidden by a broad Apache-2.0 annotation:
such an annotation could incorrectly relabel vendored, generated, or adapted material.

| Measure | Count |
|---|---:|
| Tracked files | 2,191 |
| Files with copyright metadata | 1,173 |
| Files with license metadata | 1,149 |
| Invalid SPDX expressions | 0 |
| Missing license files | 0 |
| Unused license files | 0 |

The 1,042 files without license metadata are grouped below for review, not automatically changed:

| Area | Files | Migration treatment |
|---|---:|---|
| `awake/` | 341 | Separate authored sources, generated assets, and retained upstream/native material. |
| `docs/` | 331 | Add first-party REUSE annotations except copied specifications or screenshots, which retain their origin. |
| `samples/` | 93 | Annotate authored examples; register any borrowed fixture separately. |
| `tools/`, `skills/`, `.claude/` | 166 | Identify source-owned guidance versus mirrored external skill content before annotating. |
| `snapshots/` | 43 | Record generator and reference provenance rather than claiming a default source header. |
| Other build/configuration areas | 68 | Batch annotate only after confirming they are first-party artifacts. |

## Gate progression

1. Keep `reuse lint` audit-only while each group is classified.
2. Add REUSE metadata only after the origin and license are known.
3. Record borrowed source in `docs/reference/source-provenance.json` and preserve its SPDX notices.
4. Re-run `reuse lint`; make it required only when it reaches zero missing metadata.

ScanCode and PMD CPD remain evidence-producing audits. They can flag a review lead, but cannot
prove that code was independently authored.

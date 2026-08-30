# 2026-08-23: one entry point, and three kinds of tool

Status: **done 2026-08-23.** The per-generator staleness gates, still outstanding when this was
first written, landed the same day: `tools/verify_generated.py` now gates `tailwind-scale`,
`reference-components` and `font-atlas`, and a fifth gate (`skill-spec`) was added after. The two
generators with no gate — `instantiate_roboto.py` and `svg_to_ui_image_vector.py` — say why in
`tools/README.md` rather than reading as oversights.

## The problem is not missing docs

`tools/README.md` is 251 lines with a "Choose the right lane" table and per-script tables, and
`scripts/awake ui` is a real CLI with five subcommands. Neither is the gap.

The gap is that **15 scripts exist, the CLI wraps 3 of them, and nothing says which ones can fail a
build.** Someone arriving asks "what do I run" and gets a directory listing.

| | Count |
|---|---|
| Scripts in `tools/` | 15 (`.py`/`.sh`, excluding tests) |
| Wrapped by `scripts/awake ui` | **3** — `capture_shadcn_local`, `compare_component_crops`, `generate_ui_parity_report` |
| Not wrapped | **12** — including every shadcn maintainer script |

## Three kinds, and the difference matters

Everything in `tools/` is exactly one of these. Today nothing says which, so all 15 read as equally
authoritative — and the ones that can fail a build look the same as the ones that produce a picture
for a human to squint at.

| Kind | Contract | Examples |
|---|---|---|
| **Gate** | Fails. Exit non-zero means the tree is wrong | `verify_shadcn_reference.sh` check 1, `verify_agent_skills_sync.py` |
| **Generator** | Writes a committed file. Re-running must be byte-identical | `extract_shadcn_tokens.py`, `svg_to_ui_image_vector.py`, `instantiate_roboto.py` |
| **Investigation** | Produces evidence for a human. Never fails, proves nothing on its own | `capture_shadcn_local.py`, `compare_parity.py`, `ui_preview_server.py` |

A generator is the dangerous middle: it looks like a gate (it runs, it succeeds) but it *writes*, so
a stale committed output is invisible. That already happened — `extract_shadcn_tokens.py`'s
`OUT_FILE` pointed at a module path that no longer existed and `mkdir(parents=True)` silently
created the dead tree.

**Every generator needs a matching gate.** `extract_shadcn_tokens.py` now has one
(`verify_shadcn_reference.sh`). The others do not:

| Generator | Has a staleness gate? |
|---|---|
| `extract_shadcn_tokens.py` | **yes** — `verify_shadcn_reference.sh` |
| `svg_to_ui_image_vector.py` | **not possible** — see the correction below |
| `instantiate_roboto.py` | no |
| `:awake:tailwind-generator` | no |
| `:awake:ui:font-atlas-generator` | no |

## The work

### 1. One lane per kind, under the CLI that already exists — **done**

`scripts/awake` is the entry point. Extend it rather than adding a second one:

```
awake ui …          # investigation — already there
awake verify        # every gate, in one run          <- added
awake verify --only shadcn-reference
```

`awake gen` was in the original sketch and was dropped: the generators have genuinely different
shapes (one takes an SVG path, one takes nothing, two are Gradle tasks), so a single `gen` verb
would have been a menu pretending to be an interface. They stay as they are, labelled.

`awake verify` is the one that resolves "I'm lost": it is the answer to "is anything wrong",
and it is a single command.

### 2. A staleness gate per generator — *partly wrong, corrected*

This said "the other four generators want the same wrapper". Checked while implementing, and they
are not uniform:

- `instantiate_roboto.py` and the two Gradle generators **are** gateable: fixed inputs, fixed
  outputs, re-runnable. Still to do.
- `svg_to_ui_image_vector.py` is **not**. It is one-shot — one SVG path in, one Kotlin val out,
  destination chosen by the caller — with no recorded mapping of which SVG produced which icon. There
  is nothing to re-run, so there is nothing to diff. A gate would need a manifest that does not
  exist. (`heroicons_manifest.json` covers *capture*, not conversion.)

So it is three gateable generators outstanding, not four, and one that needs a manifest first if it
is ever to be gated at all.

### 3. Label the kind in each script's own header — **done**, all 15

One line, first thing after the licence. A reader should not have to infer severity from whether a
script happens to call `sys.exit(1)`.

### 4. `tools/README.md` gains the taxonomy — **done**

It already lists what each script does. Add what each script *is*, so the lane table and the script
tables answer the same question.

## Considered and rejected

**Renaming the scripts for consistency.** Measured: **91 cross-references** from docs, skills,
agents and commands. And the verb prefixes already encode the kind 13 times out of 15 --
`verify_*` is a gate, `capture_*`/`compare_*`/`ui_preview_*` are investigation,
`extract_*`/`fetch_*`/`instantiate_*`/`svg_to_*` write things. The two apparent outliers,
`generate_ui_parity_report.py` and `generate_ui_status.py`, are not actually ambiguous: their own
headers say `INVESTIGATION` at the point of contact. Churning 91 files to fix a collision the labels
already resolve is not worth it.

**Grouping into `tools/gate/`, `tools/generator/`, `tools/investigation/`.** Same 91-reference cost,
and it is the wrong axis. The shadcn pipeline is `fetch -> extract -> verify -> capture -> compare`:
five files spanning all three kinds, used together. Grouping by kind scatters one workflow across
three folders. Kind also changes -- give `svg_to_ui_image_vector.py` a manifest and it becomes
gateable -- so encoding a classification in a path makes reclassification a file move. If grouping
ever happens, group by **domain** (`shadcn/`, `fonts/`, `icons/`, `preview/`), which keeps workflows
together.

**Moving the rest of `tools/` into skills' `scripts/` folders.** One script did move:
`svg_to_ui_image_vector.py` now lives in `skills/awake-ui-icons/scripts/`, because that skill is its
only documenter. It was the easiest possible case -- one script, one skill, one consumer -- and it
still cost about eighty reference updates, most of them doc comments inside `HeroIcons.kt` that the
script emits itself.

The other fourteen do not have a single skill owner:

| Tool | Called by | Skill-ownable? |
|---|---|---|
| `capture_shadcn_local`, `compare_parity`, `compare_component_crops`, `generate_ui_parity_report` | the `scripts/awake ui` CLI | **no** -- a CLI cannot reach into `skills/` |
| `fetch_shadcn_reference`, `extract_shadcn_tokens`, `verify_shadcn_reference` | each other, and `awake verify` | **no** -- a pipeline, and a gate |
| `verify_generated`, `verify_agent_skills_sync` | `awake verify` | **no** -- gates |
| `instantiate_roboto`, `capture_font_reference` | fonts, manually | maybe, but no font skill exists |
| `ui_preview_server`, `ui_preview_watch`, `generate_ui_status` | the CLI and humans | no |

There is also a principle worth holding: **`skills/` is agent guidance, `tools/` is repo tooling.**
The icons move already blurred it -- `generate_ui_status.py` now reaches into `skills/` for a build
status report, and `HeroIcons.kt` cites a skill path in generated source. That is a wart worth
accepting once and not fourteen times.

**A new skill for the tools.** `skills/awake-ui-verification` already is that skill -- its own
description is "which tool answers which question". The four docs that cover UI tooling are not
duplicates; they have different jobs:

| Doc | Job |
|---|---|
| `skills/awake-ui-verification` | judgment -- which tool, and when a baseline may move |
| `docs/reference/ui-validation.md` | policy -- what proof each UI type requires |
| `docs/reference/ui-parity-tool.md` | procedure -- the command sequence |
| `tools/README.md` | catalogue -- what each script is |

The real gap was that a reader landing in any one of them did not know the other three existed. All
four now carry the same pointer block, which is a fix measured in four edits rather than ninety-one.

## Not in scope

- Rewriting any script. This is labelling, one new gate wrapper, and CLI routing.
- The pixel-parity layer's own value. It is investigation and now says so
  (`docs/reference/shadcn-reference-pipeline.md`); whether it earns its keep is a separate question.
- `jni-binding-generator` and the shader tooling — same taxonomy applies, different owners.

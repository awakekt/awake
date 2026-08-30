---
name: awake-copyright-provenance
description: Record and verify lawful provenance before adapting or copying source into Awake.
---

# Awake Copyright Provenance

Use this skill before adding source adapted from an external repository, documentation example, or
generated source whose template is external. It does not apply to API-compatible clean-room work:
record the public specification in normal design documentation instead.

1. Prefer a dependency, public specification, or clean-room implementation over copied source.
2. Identify the upstream URL, immutable revision, copyright holder, and SPDX license before copying.
3. Confirm the license is compatible with Apache-2.0. If it is missing or uncertain, do not copy.
4. Preserve third-party SPDX ownership/license metadata. Use SPDX snippet markers for only the
   borrowed block; do not claim it with Awake's project copyright header.
5. Add the required `file` or `snippet` entry to `docs/reference/source-provenance.json`, using
   the schema and review criteria in `docs/reference/source-provenance.md`.
6. Run `python3 tools/verify_source_provenance.py --staged` before committing.

Do not describe code as "original" solely because a scanner found no match. REUSE, ScanCode, and
PMD CPD provide compliance and similarity evidence, not proof of independent creation.

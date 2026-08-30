# Kotlin Package Namespace Migration Plan

**Date:** 2026-08-29  
**Status:** `Active`  
**Prerequisite for:** [Maven Central publication](2026-08-29-maven-central-publication-plan.md)

## Objective

Move Awake's public Kotlin package namespace from:

```text
io.github.awakelab.awake.*
```

to:

```text
io.github.awakelab.awake.*
```

The Gradle Maven group remains a separate concern:

```text
io.github.awake-lab
```

The hyphen is valid in a Maven group ID but not in a Kotlin package. The package and Maven group
therefore intentionally do not have identical spelling.

## Why This Must Happen Before Maven Stable

Kotlin package names are part of the source and binary API. Publishing first and changing packages
afterward would force every consumer to update imports and would create avoidable compatibility
debt. This is the final breaking namespace pass before the first stable Maven release, not a
routine cleanup to perform after publication.

The existing naming work already separates this from Gradle module-path changes:

- [Application seam and module naming plan](archive/2026-08-09-application-seam-and-module-naming-plan.md)
- [UI refactor audit, B11 package-root rename](../audits/2026-08-17-ui-refactor-vs-recreate-audit.md)
- [Game naming generalization plan](../../audits/2026-08-19-game-naming-generalization-plan.md)

## Scope

### Included

- `package` declarations in `src/**/kotlin` and `src/**/java`
- Kotlin and Java imports and fully qualified references
- Android `namespace` declarations
- Native interop package references and generated-source inputs
- Tests, samples, benchmarks, tools, and build-logic source references
- Documentation, examples, skills, and scripts that name the old package
- Source directory moves from `io/github/awakelab/awake` to
  `io/github/awakelab/awake`

### Excluded

- Maven coordinates and artifact IDs
- Gradle module-path renames already covered by the module naming plan
- Package names belonging to third-party dependencies
- Historical archive text, unless it is presented as current consumer guidance
- Generated `build/`, `.gradle/`, IDE, and native toolchain output

## Refactor Tool

Add a deterministic script at `tools/refactor_package_namespace.py`. It should:

- Replace only the exact token `io.github.awakelab.awake` with
  `io.github.awakelab.awake`.
- Scan tracked text files through Git rather than walking generated output.
- Include Kotlin, Java, Gradle Kotlin DSL, XML, Markdown, scripts, and configuration files.
- Preserve line endings and file encoding.
- Support `--check` to report remaining old references without editing.
- Support `--apply` to rewrite files and move matching package directories.
- Print every changed file and exit non-zero when `--check` finds a stale reference.
- Refuse to run with a dirty worktree unless `--allow-dirty` is explicitly provided.

The script is a repeatable migration aid, not a permanent source-generation step. Once the
migration is complete, keep it as a checked-in historical maintenance tool or move it to the
refactor tooling archive with a clear note that the migration has landed.

## Execution Phases

### 1. Freeze the Target Namespace

- Confirm `io.github.awakelab.awake.*` as the final public package root.
- Record the decision before editing source files.
- Freeze unrelated package and module renames until this pass is complete.
- Capture a baseline of the current compile and test commands.

### 2. Inventory the Rename

- Run `git grep` for `io.github.awakelab.awake`.
- Classify matches as source, build configuration, generated input, documentation, historical
  record, or third-party reference.
- Produce a checked-in or CI-readable inventory so missed package surfaces are visible.
- Identify `internal` visibility boundaries that may break when files move between modules or
  source sets.

### 3. Build the Refactor Script

- Implement `tools/refactor_package_namespace.py` with dry-run and check modes.
- Add unit tests for exact replacement, non-replacement of similar strings, generated-output
  exclusion, directory moves, dirty-worktree refusal, and idempotent re-runs.
- Run the script in dry-run mode and review the complete file list before applying it.

### 4. Rename Source Packages and Folders

- Apply the script to source, tests, samples, build logic, and live documentation.
- Move every matching source directory to the new package path.
- Update Android `namespace` values and any package-sensitive native configuration.
- Do not mix behavior changes, module moves, or API redesign into this commit.
- Keep the migration reviewable as separate commits if the touched surface is too large for one
  review, but run the script from one frozen baseline.

### 5. Repair and Verify References

- Run `tools/refactor_package_namespace.py --check` and require zero old references outside
  explicitly historical documents.
- Run the repository's package, namespace, and duplicate-coordinate checks.
- Re-run code formatting and license/header checks.
- Inspect generated Kotlin metadata and Android manifests for the new namespace.

### 6. Compile the Multiplatform Matrix

At minimum, verify the public modules and consumer paths that will be published:

```bash
./gradlew \
  :awake:engine:bootstrap:compileKotlinJvm \
  :awake:backend:vulkan:compileKotlinDesktop \
  :awake:backend:webgpu:compileKotlinWasmJs \
  :awake:asset:shaders:compileKotlinJvm
```

Also run the focused desktop tests and Android/iOS compilation on hosts that provide those
toolchains. No Maven publication work starts until the namespace migration is green across the
supported targets.

### 7. Validate the External Consumer

- Update the public `awake-template` references to the new package namespace.
- Test with the local `../awaken` composite build first.
- Then disable composite substitution and test using published or locally staged artifacts.
- Confirm the template has no old imports and no dependency on the old package root.

### 8. Release Gate

The migration is complete only when:

- No live source, build, sample, or consumer documentation uses the old package root.
- All source folders match their declarations.
- All supported target compilations pass.
- Public API and binary compatibility reports are intentionally regenerated.
- `awake-template` builds with the new package namespace.
- The Maven publication plan is updated to use the post-migration API surface.
- A release note clearly identifies this as the final pre-stable breaking package migration.

## Commit Boundaries

Use these reviewable boundaries where practical:

1. Refactor script and script tests.
2. Source package declarations, imports, and folder moves.
3. Build configuration, namespaces, samples, and documentation.
4. API baselines, release notes, and template consumer update.

Do not publish Maven artifacts between these commits. Publish only after the complete release gate
passes.

## Risks

- Package changes break downstream imports and binary references by design.
- Kotlin/Native and Android namespace declarations may compile differently from JVM sources, so
  JVM-only verification is insufficient.
- Historical documentation can contain the old namespace legitimately; the check must distinguish
  current guidance from archival records rather than deleting historical context.
- A package move can expose accidental `internal` cross-module coupling. Treat those failures as
  architecture findings, not as reasons to add compatibility aliases before the first release.

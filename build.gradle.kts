/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.library.kmp) apply false
    alias(libs.plugins.vanniktech.publish) apply false
    alias(libs.plugins.binary.compat)
    alias(libs.plugins.kover)
}

apiValidation {
    ignoredProjects += setOf(
        "ui-showcase", "engine-showcase", "studio", "server",
        "benchmark", "generator", "android-native", "font-atlas-generator",
        // `editor` is deliberately absent: it is a library, not an application, and its plugin
        // contract negotiates an EditorPluginApiVersion that means nothing if the surface it
        // versions is untracked. See D32. Every remaining entry here is a sample or a build tool.
        "tailwind-generator", "testing",
        // ponytail: `webgpu` is published but unvalidated, because its `jvmToolchain(25)` (needed
        // for the FFM API) emits class-file major 69 and binary-compatibility-validator 0.17.0
        // bundles an ASM that cannot read it -- `desktopApiBuild` dies with "Unsupported class
        // file major version 69" before comparing anything. This is the whole `verify` job, so
        // the alternative was a release that cannot be cut. Drop this entry once BCV ships an
        // ASM new enough, and run `./gradlew :awake:backend:webgpu:desktopApiDump` to seed the
        // dump it has never had.
        "webgpu",
        // `parity` for the same reason, one step removed: it links the WebGPU backend, so its own
        // toolchain is 25 too. It is a test harness rather than a shipped surface, so nothing is
        // lost by leaving it untracked.
        "parity",
    )
}

// Raised as the engine's own coverage rises; never lowered to make a red build pass.
//
// 98.3% line, measured 2026-08-22, up from 90.5%. Not 100, and 100 is not the target: what remains
// is Kotlin's own `$DefaultImpls` holders, `$default` argument bridges and `protected set`
// accessors -- code nobody wrote and no test can call directly. The holders are excluded below so
// the number counts authored code; chasing the last point by testing compiler output would buy a
// rounder figure and no confidence.
val composeMinLineCoverage = 90

// Coverage is scoped to :awake:compose:* rather than the whole repo. A repo-wide threshold
// would fail on day one across ~30 modules whose coverage nobody has measured, and a gate
// that is red everywhere is not a gate -- the same way spotlessCheck currently fails in 26
// modules without anyone noticing. Widen module by module as each earns a number.
//
// Kover measures JVM-executed tests only: "Source code outside the common and JVM source
// sets is ignored". These modules also run on wasmJs and iOS Native, and those runs are
// invisible here -- so this percentage is the JVM subset, not the target matrix.
dependencies {
    kover(project(":awake:compose:runtime"))
    kover(project(":awake:compose:ui"))
    kover(project(":awake:compose:foundation"))
}

kover {
    reports {
        filters {
            excludes {
                // Kotlin emits these; nobody writes them and no test can call them directly.
                // Counting them would make the metric measure the compiler rather than the suite.
                classes("*\$DefaultImpls", "*.DefaultImpls")
            }
        }
        total {
            verify {
                rule {
                    minBound(composeMinLineCoverage)
                }
            }
        }
    }
}

// koverVerify is not wired into `check` by Kover itself. Note this repo's CI runs
// `./gradlew detekt`, not `check`, so the threshold is only enforced where koverVerify is
// invoked explicitly -- add a CI step for it if coverage should actually gate a merge.
tasks.named("koverVerify") {
    dependsOn(
        ":awake:compose:runtime:desktopTest",
        ":awake:compose:ui:desktopTest",
        ":awake:compose:foundation:desktopTest",
    )
}
tasks.matching { it.name == "check" }.configureEach {
    dependsOn(tasks.named("koverVerify"))
}

// Version comes from the latest v* git tag, so publishing is "tag + push" and the
// number can never drift from the tag:
//   HEAD exactly on v0.1.0-dev.1  ->  0.1.0-dev.1          (publishable, immutable)
//   3 commits after that tag      ->  0.1.0-dev.2-SNAPSHOT (local/CI only, never released)
//   no tag reachable              ->  0.1.0-dev.0-SNAPSHOT
val gitDerivedVersion: String = run {
    val describe = runCatching {
        providers.exec {
            commandLine(
                "git",
                "-C",
                rootDir.absolutePath,
                "describe",
                "--tags",
                "--match",
                "v*",
                "--always",
            )
        }.standardOutput.asText.get().trim()
    }.getOrDefault("")
    val hasNoReleaseTag = describe.matches(Regex("^[0-9a-f]{7,}$"))
    val exact = Regex("""^v(.+?)-(\d+)-g[0-9a-f]+$""").find(describe)
    when {
        describe.isEmpty() || hasNoReleaseTag -> "0.1.0-dev.0-SNAPSHOT"
        exact == null -> describe.removePrefix("v")
        else -> {
            val base = exact.groupValues[1]
            val bumped = Regex("""(\d+)$""").replace(base) { (it.value.toInt() + 1).toString() }
            "$bumped-SNAPSHOT"
        }
    }
}

allprojects {
    // Maven namespace and package root: com.awakekt.awake.
    //
    // The group carries the module's *parent path*, and that is load-bearing rather than tidy.
    // Gradle identifies a project by the capability `group:name`, and `name` is only the last path
    // segment -- so with one flat group, `:awake:compose:runtime` and `:awake:scene:runtime` both
    // claim `com.awakekt.awake:runtime`. Gradle resolves that by substituting one project for the
    // other, and the damage takes two shapes. At configuration time it can produce a circular task
    // graph pointing at neither culprit -- scene:runtime ended up depending on itself through its
    // own jar. At runtime the loser's classes never reach a consumer's classpath at all, which is
    // where this repo's NoClassDefFoundError/IrLinkageError crashes on UiAnimatedVisibilityKt came
    // from. That one was already fixed by hand, in :awake:ui:animation alone; this generalises it.
    //
    // Five names collide without it: `animation` (core, ui), `benchmark` (ecs, ui,
    // scene:rendering), `physics` (awake, scene), `runtime` (compose, scene) and -- most live for
    // the port in flight -- `ui` (:awake:ui, :awake:compose:ui).
    //
    // Fixed here rather than by renaming the projects, because a project's name *is* the last
    // segment of its path -- renaming breaks every `project(":awake:...")` reference at once, while
    // this changes only published coordinates. `archivesName` does not work at all: substitution
    // resolves on capability, not archive name.
    group = buildString {
        append("com.awakekt.awake")
        // The leading `awake` segment is dropped: the group already says awake-lab, and
        // `com.awakekt.awake.awake.compose` reads as a stutter.
        project.parent?.path?.removePrefix(":")?.removePrefix("awake")?.removePrefix(":")
            ?.replace(':', '.')
            ?.takeIf { it.isNotEmpty() }
            ?.let { append('.').append(it) }
    }
    version = gitDerivedVersion
}

// Two projects sharing `group:name` is not a naming nit -- Gradle substitutes one for the other and
// the failure surfaces as a circular task graph in an unrelated module, which cost a real debugging
// session to trace. Checked at configuration time so it cannot be skipped by running a narrower
// task, which is exactly how the last one hid: every per-module compile passed.
gradle.projectsEvaluated {
    // Every project, not just leaves. A container that shares coordinates with a real module is
    // the same hazard, and filtering to leaves under-reported: five names duplicate here
    // (animation, benchmark, physics, runtime, ui), and a leaf-only pass saw two of them.
    val byCoordinates = allprojects
        .groupBy { "${it.group}:${it.name}" }
        .filterValues { it.size > 1 }
    check(byCoordinates.isEmpty()) {
        val clashes = byCoordinates.entries.joinToString("\n") { (coordinates, projects) ->
            "  $coordinates <- ${projects.joinToString(", ") { it.path }}"
        }
        "Projects share Maven coordinates, so Gradle will substitute one for the other:\n$clashes\n" +
                "Give them distinct groups (see the group derivation in allprojects) or distinct names."
    }
}

tasks.register("developerDocs") {
    group = "documentation"
    description = "Build developer-facing API references and tutorial artifacts."

    // Dokka targets are derived, not listed. The hand-written list named eight projects that had
    // stopped existing -- `:awake:core` became a container of subprojects, `:awake:ui:ui-headless`
    // was renamed -- and because a bad task path only fails when the task is actually run, this
    // aggregate had been unrunnable for a while with nothing saying so.
    dependsOn(
        provider {
            subprojects.mapNotNull { sub ->
                sub.tasks.findByName("dokkaGeneratePublicationHtml")?.let { "${sub.path}:${it.name}" }
            }
        },
    )

    // The named reports stay explicit: each is one curated artifact, not a per-module default.
    dependsOn(
        ":awake:engine:bootstrap:desktopTest",
        ":awake:engine:bootstrap:gameDslTutorialDocsReport",
        ":awake:engine:bootstrap:uiDslTutorialDocsReport",
    )
}

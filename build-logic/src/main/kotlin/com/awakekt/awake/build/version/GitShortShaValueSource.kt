/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.version

import com.awakekt.awake.build.tasks.runGit
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

/**
 * The abbreviated commit of HEAD in [Parameters.directory], or [UNKNOWN_COMMIT] when it is not in a git
 * checkout, git is not installed, or the checkout has no commit yet.
 *
 * A value source, so the configuration cache records the answer and re-reads it each build: a new
 * commit changes the stamp without invalidating anything else.
 */
abstract class GitShortShaValueSource : ValueSource<String, GitShortShaValueSource.Parameters> {
    interface Parameters : ValueSourceParameters {
        val directory: DirectoryProperty
    }

    override fun obtain(): String = shortCommit { runGit(parameters.directory.get().asFile, *it) }
}

/** [git] runs `git` with the arguments given; any failure reads as no commit. */
internal fun shortCommit(git: (Array<String>) -> String): String =
    try {
        git(arrayOf("rev-parse", "--short", "HEAD")).trim().ifEmpty { UNKNOWN_COMMIT }
    } catch (_: Exception) {
        UNKNOWN_COMMIT
    }

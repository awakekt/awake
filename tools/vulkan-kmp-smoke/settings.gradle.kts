/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
// Standalone on purpose: NOT included in the root build, so resolving vulkan-kmp here goes
// through real Maven metadata (mavenLocal) exactly the way an external consumer's build
// would -- no project substitution can paper over broken published coordinates.
rootProject.name = "vulkan-kmp-smoke"

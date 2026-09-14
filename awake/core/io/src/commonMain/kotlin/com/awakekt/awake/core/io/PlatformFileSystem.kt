/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.io

/** Creates the default platform filesystem rooted at [root]. */
expect fun createPlatformFileSystem(root: String? = null): FileSystem

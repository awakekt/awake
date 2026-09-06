/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio.files

actual fun createPlatformFileOps(): StudioFileOps = InMemoryStudioFileOps()

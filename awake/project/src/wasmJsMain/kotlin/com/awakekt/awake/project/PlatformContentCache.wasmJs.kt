/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

/** The origin's IndexedDB, which reads as a miss wherever the browser blocks or fills it. */
internal actual fun createPlatformContentCache(namespace: String): ContentCache? = IndexedDbContentCache(namespace)

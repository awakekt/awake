/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/**
 * A [FileSystemContentCache] in the app's `Library/Caches`, which the system doesn't back up and may
 * empty when the device runs short of space; the next run then downloads again.
 */
internal actual fun createPlatformContentCache(namespace: String): ContentCache? =
    NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
        .firstOrNull()
        ?.toString()
        ?.let { directoryContentCache(it, namespace) }

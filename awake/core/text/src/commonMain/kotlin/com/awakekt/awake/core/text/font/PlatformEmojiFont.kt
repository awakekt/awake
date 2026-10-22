/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

/**
 * Reads the host platform's colour emoji font file bytes if present.
 */
internal expect fun readPlatformEmojiFontBytes(): ByteArray?

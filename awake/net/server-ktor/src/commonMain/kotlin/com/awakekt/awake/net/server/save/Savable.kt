/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.server.save

/**
 * An entity or object that can be saved transactionally by [SaveScheduler].
 */
interface Savable {
    /** Unique identity key under which this item is persisted. */
    val saveKey: String
}

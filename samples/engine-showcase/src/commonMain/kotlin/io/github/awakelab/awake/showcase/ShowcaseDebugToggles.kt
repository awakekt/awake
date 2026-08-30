/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.showcase

/**
 * What the showcase draws on top of the scene, for whoever is watching it.
 *
 * Off by default. The navigation grid is a diagnostic — a marker on every sample an agent cannot
 * stand on — and it reads as part of the demonstration rather than as an overlay when it is always
 * on, which is exactly the confusion it caused.
 *
 * An object, like the example drivers themselves: the drivers are singletons reached from a global
 * showcase list, so a per-module instance would have nowhere to be handed to them from.
 */
internal object ShowcaseDebugToggles {
    /** Draws unwalkable navigation samples and each chaser's current route. */
    var showNavGrid: Boolean = false
}

/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.camera

/**
 * Which held pointer button orbits a camera. A finger is not a button: on a touch screen one finger
 * drag orbits whichever is chosen, since a phone has no secondary button to hold.
 *
 * A game whose primary button walks and steers, on a click-to-move desktop client, picks
 * [SecondaryOnly], so a primary drag is the game's and only a secondary drag turns the view.
 */
enum class CameraOrbitButtons {
    /** A primary or a secondary drag orbits. The default, and how every camera orbited before this choice. */
    PrimaryOrSecondary,

    /** Only a secondary drag orbits, or one finger on a touch screen; a primary mouse drag is left to the game. */
    SecondaryOnly,
    ;

    /**
     * Whether a drag with these buttons held may orbit.
     *
     * @param primaryDown Whether the primary pointer, a mouse's or a finger's, is down.
     * @param secondaryDown Whether the secondary pointer button is down.
     * @param touch Whether the player last used a touch screen, so the primary pointer is a finger.
     */
    fun allows(primaryDown: Boolean, secondaryDown: Boolean, touch: Boolean): Boolean = when (this) {
        PrimaryOrSecondary -> primaryDown || secondaryDown
        SecondaryOnly -> secondaryDown || (touch && primaryDown)
    }
}

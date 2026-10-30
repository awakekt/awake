/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

/**
 * What a game can tell about where it runs, read once at launch: the page's [origin] on the web, and
 * whether the device is [PrimaryInput.Touch]-first or [PrimaryInput.Pointer]-first. A platform's
 * factory (`browserLaunchEnvironment()`, `desktopLaunchEnvironment()`,
 * `androidLaunchEnvironment(context)`, `iosLaunchEnvironment()`) builds one; register it with
 * `service(LaunchEnvironment::class, …)`.
 *
 * It sits beside [LaunchParameters] rather than inside it: the parameters are what the launcher was
 * given, and this is what the host reports about itself. Build one directly in a test.
 *
 * | Platform | [origin] | [primaryInput] |
 * |---|---|---|
 * | Web | the page's scheme, host and port; null for `file:` and other opaque origins | `Touch` when `matchMedia('(pointer: coarse)')` matches |
 * | Desktop | null | `Pointer` |
 * | Android | null | `Touch` when the device has the touchscreen system feature |
 * | iOS | null | `Touch` |
 *
 * **Both values are hints, not security boundaries.** The origin is whoever serves this copy of the
 * page, and the primary input is the platform's guess at the device. Use them to pick defaults, such
 * as a server address or a HUD layout, and never to decide what a player may do.
 */
class LaunchEnvironment(
    /** Where the page was served from, or null off the web and for an opaque origin. See [LaunchOrigin]. */
    val origin: LaunchOrigin?,
    /** Whether the device was touch-first or pointer-first at launch. See [PrimaryInput]. */
    val primaryInput: PrimaryInput,
) {
    override fun equals(other: Any?): Boolean =
        other is LaunchEnvironment && origin == other.origin && primaryInput == other.primaryInput

    override fun hashCode(): Int = origin.hashCode() * HASH_PRIME + primaryInput.hashCode()

    override fun toString(): String = "LaunchEnvironment(origin=$origin, primaryInput=$primaryInput)"
}

/**
 * The kind of input a device is built around, read at launch, so a game can choose a touch HUD or a
 * pointer HUD before the player has touched anything.
 *
 * **It can change while the game runs, and this value does not follow.** A tablet gains a mouse or
 * a keyboard cover, a convertible laptop folds into a tablet, a phone is cast to a screen, a browser's
 * device emulation is switched on. The value stays what it was at launch. A game that wants to follow
 * the player switches on the input it actually receives (a touch, a pointer move) and uses this only
 * as the starting choice.
 */
enum class PrimaryInput {
    /** The primary input is a finger: a phone, a tablet, a browser whose primary pointer is coarse. */
    Touch,

    /** The primary input is a mouse or trackpad: a desktop, a browser whose primary pointer is fine, an Android device with no touchscreen. */
    Pointer,
}

/** [PrimaryInput.Touch] when the primary pointer is coarse, as `matchMedia('(pointer: coarse)')` reports it. */
internal fun primaryInputFromCoarsePointer(coarse: Boolean): PrimaryInput =
    if (coarse) PrimaryInput.Touch else PrimaryInput.Pointer

private const val HASH_PRIME = 31

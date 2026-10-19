/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.server.phase

import com.awakekt.awake.net.TransportSession

/**
 * The discrete lifecycle phases a connected player session progresses through.
 */
enum class SessionPhase {
    /** Connection accepted; awaiting initial hello / authentication payload. */
    Accept,

    /** Authenticating credentials. */
    Authenticating,

    /** Authenticated; in pre-play phase (e.g. character select, lobby, match staging). */
    PrePlay,

    /** Active gameplay simulation. Inbound messages route to [com.awakekt.awake.net.server.feature.GameFeature]s. */
    Playing,

    /** Connection terminated and cleaned up. */
    Closed,
}

/**
 * Pluggable authenticator verifying credentials or tokens from incoming connection messages.
 *
 * @param P The player or session representation.
 * @param M The base message type.
 */
fun interface Authenticator<P, M> {
    /**
     * Authenticates an incoming session using [message].
     *
     * @return [AuthResult.Success] containing the authenticated player representation, or [AuthResult.Denied] with a reason.
     */
    suspend fun authenticate(session: TransportSession, message: M): AuthResult<P>
}

/**
 * Result of an authentication attempt.
 */
sealed interface AuthResult<out P> {
    /** Authentication succeeded with [player]. */
    data class Success<P>(val player: P) : AuthResult<P>

    /** Authentication failed with [reason]. */
    data class Denied(val reason: String) : AuthResult<Nothing>
}

/**
 * Pluggable handler for the [SessionPhase.PrePlay] phase (e.g. character select, deck selection, lobby readiness).
 *
 * @param P The player or session representation.
 * @param M The base message type.
 */
fun interface PrePlayHandler<P, M> {
    /**
     * Processes [message] while [player] is in pre-play.
     *
     * @return [PrePlayResult.EnterPlay] to transition to active play, [PrePlayResult.StayInPrePlay] to remain in pre-play,
     * or [PrePlayResult.Refused] to reject and disconnect.
     */
    suspend fun handlePrePlay(player: P, message: M): PrePlayResult
}

/**
 * Result of a pre-play message interaction.
 */
sealed interface PrePlayResult {
    /** Stay in [SessionPhase.PrePlay]. */
    data object StayInPrePlay : PrePlayResult

    /** Transition to [SessionPhase.Playing]. */
    data object EnterPlay : PrePlayResult

    /** Denied with [reason], terminating the session. */
    data class Refused(val reason: String) : PrePlayResult
}

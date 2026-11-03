/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.prediction

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ReconcileTest {
    private val config = ReconcileConfig()

    /**
     * An entity whose prediction of the acknowledged input left it at the origin, drawn [ahead] metres
     * further along x (its unacknowledged steps), and a server that has it [drift] along x from the
     * origin: a fresh, unread, trusted acknowledgement.
     */
    private fun input(drift: Float, ahead: Float = 1f) = ReconcileInput().apply {
        ackSeq = 10
        ackFresh = true
        ackUnread = true
        ackKnown = true
        server(drift, 0f, 0f)
        drawn(ahead, 0f, 0f)
    }

    @Test
    fun aMatchingSnapshotCorrectsNothing() {
        assertSame(Correction.None, reconcile(input(0f).apply { ackUnread = false }, config))
    }

    @Test
    fun aSmallDriftAtTheAckIsBlendedNotCorrected() {
        val c = reconcile(input(drift = 0.4f, ahead = 3f), config)
        assertEquals(Correction.Blend(0.4f, 0f, 0f), c)
        assertTrue(!c.corrects)
    }

    @Test
    fun aDriftPastTheCorrectDistanceShiftsByTheDriftAlone() {
        // Drawn 4 m ahead of the ack's pose, the server 2 m from it: shift by 2, keeping the 4 m walked since.
        val c = reconcile(input(drift = 2f, ahead = 4f), config)
        assertEquals(Correction.Shift(2f, 0f, 0f), c)
        assertTrue(c.corrects)
    }

    @Test
    fun theCorrectThresholdOnBothSides() {
        assertIs<Correction.Blend>(reconcile(input(drift = 1.5f, ahead = 5f), config))
        assertIs<Correction.Shift>(reconcile(input(drift = 1.51f, ahead = 5f), config))
    }

    @Test
    fun theDrawnPoseWithinTheCorrectDistanceCorrectsNothing() {
        // The server's drift is 2 m back, but its pose is within 1.5 m of the one drawn: nothing to see.
        assertIs<Correction.Blend>(reconcile(input(drift = -2f, ahead = -3f), config))
        assertIs<Correction.Shift>(reconcile(input(drift = -2f, ahead = -0.4f), config))
    }

    @Test
    fun theWalkSinceTheAckAloneNeverCorrects() {
        // The server agrees at the ack; the pose drawn is 1.4 s of 6 m/s ahead, all of it unacknowledged.
        assertIs<Correction.Blend>(reconcile(input(drift = 0f, ahead = 8.4f), config))
    }

    @Test
    fun theFarThresholdOnBothSides() {
        assertEquals(Correction.Shift(10f, 0f, 0f), reconcile(input(drift = 10f, ahead = 0f), config))
        val far = reconcile(input(drift = 10.1f, ahead = 0f), config)
        assertEquals(Correction.Snap(10.1f, 0f, 0f, SnapReason.Far), far)
    }

    @Test
    fun aStaleAckCorrectsNothing() {
        assertIs<Correction.Shift>(reconcile(input(drift = 3f), config))
        // Not fresh: the server's acknowledgement stood still, so it hasn't heard the client lately.
        assertIs<Correction.Blend>(reconcile(input(drift = 3f).apply { ackFresh = false }, config))
        assertSame(Correction.None, reconcile(input(drift = 3f).apply { ackFresh = false; ackUnread = false }, config))
        assertSame(Correction.None, reconcile(input(drift = 20f).apply { ackFresh = false; ackUnread = false }, config))
    }

    @Test
    fun tooManyGuessedTicksCorrectNothing() {
        assertIs<Correction.Shift>(reconcile(input(drift = 3f).apply { guessedTicks = 2 }, config))
        assertSame(Correction.None, reconcile(input(drift = 3f).apply { guessedTicks = 3 }, config))
        assertIs<Correction.Blend>(reconcile(input(drift = 1f).apply { guessedTicks = 2 }, config))
        assertSame(Correction.None, reconcile(input(drift = 1f).apply { guessedTicks = 3 }, config))
    }

    @Test
    fun noHistoryAtTheAckSnapsOnlyPastTheCorrectDistance() {
        val lost = input(drift = 0f).apply { ackKnown = false }
        lost.drawn(1.6f, 0f, 0f)
        assertEquals(Correction.Snap(0f, 0f, 0f, SnapReason.NoHistory), reconcile(lost, config))
        lost.drawn(1.5f, 0f, 0f)
        // Within reach, and with no pose to blend from: nothing.
        assertSame(Correction.None, reconcile(lost, config))
    }

    @Test
    fun planarIgnoresHeight() {
        val climbing = input(drift = 0f, ahead = 0f).apply { server(1f, 3f, 0f) }
        assertEquals(Correction.Shift(1f, 3f, 0f), reconcile(climbing, config))
        assertEquals(Correction.Blend(1f, 0f, 0f), reconcile(climbing, config.copy(planar = true)))
    }

    @Test
    fun aGamesRuleSnapsWithItsOwnReason() {
        val rules = ReconcileRules { input -> Hold.takeIf { input.drawnDrift() > 0f } }
        val small = input(drift = 0.2f, ahead = 1f)
        assertEquals(Correction.Snap(0.2f, 0f, 0f, Hold), reconcile(small, config, rules))
        assertEquals("hold", (reconcile(small, config, rules) as Correction.Snap).reason.label)
        // The rule comes first, over a stale ack too.
        assertIs<Correction.Snap>(reconcile(small.apply { ackFresh = false }, config, rules))
        // Without the rule the drift decides.
        assertIs<Correction.Blend>(reconcile(small, config))
        // A rule that answers null leaves it to the drift.
        assertIs<Correction.Blend>(reconcile(small.drawn(0.2f, 0f, 0f), config, rules))
    }

    @Test
    fun theCapEasesTheSpeedToZeroOverTheEaseBeforeIt() {
        // The default cap: the server's half-second guess window, then a quarter second's ease.
        val waiting = input(drift = 0f).apply { ackFresh = false; ackUnread = false }
        waiting.secondsSinceOldestUnheard = 0.5
        assertSame(Correction.None, reconcile(waiting, config))
        waiting.secondsSinceOldestUnheard = 0.5 + 0.25 / 4
        assertEquals(0.75f, (reconcile(waiting, config) as Correction.Wait).speedScale, 1e-5f)
        waiting.secondsSinceOldestUnheard = 0.625
        assertEquals(0.5f, (reconcile(waiting, config) as Correction.Wait).speedScale, 1e-5f)
        waiting.secondsSinceOldestUnheard = 0.75
        assertSame(Correction.Wait.Stopped, reconcile(waiting, config))
        waiting.secondsSinceOldestUnheard = Double.POSITIVE_INFINITY
        assertSame(Correction.Wait.Stopped, reconcile(waiting, config))
        // No ease: full speed up to the cap, stopped at it.
        val sudden = config.copy(capEaseSeconds = 0.0)
        waiting.secondsSinceOldestUnheard = 0.74
        assertSame(Correction.None, reconcile(waiting, sudden))
        waiting.secondsSinceOldestUnheard = 0.75
        assertSame(Correction.Wait.Stopped, reconcile(waiting, sudden))
    }

    @Test
    fun theSpeedScaleOnBothSidesOfTheEase() {
        assertEquals(1f, speedScale(0.0, config))
        assertEquals(1f, speedScale(0.5, config))
        assertEquals(0.5f, speedScale(0.625, config), 1e-5f)
        assertEquals(0f, speedScale(0.75, config))
        assertEquals(0f, speedScale(9.0, config))
    }

    @Test
    fun theCapIsTimedFromTheNewestInputTheServerHeardNotItsGuesses() {
        val history = PredictionHistory()
        for (seq in 1L..30L) history.record(seq, seq * 0.2f, 0f, 0f, timeSeconds = seq / 30.0)
        // The server acknowledges input 16, but walked 15 of those ticks on a guess: it heard input 1.
        // Its pose is 2.2 m behind where the client's prediction of input 16 left it.
        val input = ReconcileInput().apply { ackSeq = 16; guessedTicks = 15; ackFresh = true; ackUnread = true }
        input.server(1f, 0f, 0f).drawn(6f, 0f, 0f).lookUp(history, nowSeconds = 1.0)
        assertEquals(1L, input.heardSeq)
        assertEquals(1.0 - 2 / 30.0, input.secondsSinceOldestUnheard, 1e-9, "input 2 is the oldest it hasn't heard")
        assertSame(Correction.Wait.Stopped, reconcile(input, config))
        // Had it heard input 16 itself, the oldest unheard input would be 17, under half a second old.
        input.guessedTicks = 0
        input.lookUp(history, nowSeconds = 1.0)
        assertEquals(1.0 - 17 / 30.0, input.secondsSinceOldestUnheard, 1e-9)
        assertIs<Correction.Shift>(reconcile(input, config))
    }

    @Test
    fun theCapLiftsAsSoonAsTheServerHearsAgain() {
        val history = PredictionHistory()
        // Inputs 1..40 sent 30 a second; the server heard input 1 and then nothing for over a second.
        for (seq in 1L..40L) history.record(seq, seq * 0.2f, 0f, 0f, timeSeconds = seq / 30.0)
        val input = ReconcileInput().apply { ackSeq = 1; server(0.2f, 0f, 0f); drawn(8f, 0f, 0f) }
        input.lookUp(history, nowSeconds = 41 / 30.0)
        assertSame(Correction.Wait.Stopped, reconcile(input, config))
        // The held inputs arrive: the server acknowledges input 40, agreeing with the prediction.
        input.ackSeq = 40
        input.ackFresh = true
        input.ackUnread = true
        input.server(8f, 0f, 0f).lookUp(history, nowSeconds = 41 / 30.0)
        assertEquals(0.0, input.secondsSinceOldestUnheard)
        val blend = assertIs<Correction.Blend>(reconcile(input, config))
        assertEquals(0f, blend.dx, 1e-4f)
    }

    @Test
    fun nothingWaitsBeforeTheServerHeardAnything() {
        val history = PredictionHistory()
        for (seq in 1L..40L) history.record(seq, 0f, 0f, 0f, timeSeconds = seq / 30.0)
        val input = ReconcileInput().lookUp(history, nowSeconds = 5.0)
        assertEquals(0.0, input.secondsSinceOldestUnheard)
    }

    @Test
    fun theCapWinsOverAShiftAndABlendButNotASnap() {
        val late = input(drift = 3f).apply { secondsSinceOldestUnheard = 5.0 }
        assertSame(Correction.Wait.Stopped, reconcile(late, config))
        assertSame(Correction.Wait.Stopped, reconcile(input(drift = 0.5f).apply { secondsSinceOldestUnheard = 5.0 }, config))
        assertIs<Correction.Shift>(reconcile(late.apply { secondsSinceOldestUnheard = 0.0 }, config))
        val far = input(drift = 20f).apply { secondsSinceOldestUnheard = 5.0 }
        assertEquals(SnapReason.Far, assertIs<Correction.Snap>(reconcile(far, config)).reason)
        val lost = input(drift = 3f).apply { ackKnown = false; secondsSinceOldestUnheard = 5.0 }
        assertEquals(SnapReason.NoHistory, assertIs<Correction.Snap>(reconcile(lost, config)).reason)
    }

    @Test
    fun aRuleReadsTheGamesOwnStateNotCoresInput() {
        // State Core knows nothing of, owned by the game and captured by its rule.
        class GamePlayer(var stunBegan: Boolean = false)
        val player = GamePlayer()
        val rules = ReconcileRules { input -> Hold.takeIf { player.stunBegan && input.drawnDrift(planar = true) > 0f } }
        val input = input(drift = 0.2f, ahead = 1f)
        assertIs<Correction.Blend>(reconcile(input, config, rules))
        player.stunBegan = true
        assertEquals(Correction.Snap(0.2f, 0f, 0f, Hold), reconcile(input, config, rules))
        // The rule comes before the cap too.
        assertEquals(Correction.Snap(0.2f, 0f, 0f, Hold), reconcile(input.apply { secondsSinceOldestUnheard = 9.0 }, config, rules))
    }

    @Test
    fun theDefaultCapIsTheServersGuessWindowAndTheEase() {
        assertEquals(0.75, ReconcileConfig().predictionCapSeconds)
        assertEquals(1.25, ReconcileConfig(serverGuessWindowSeconds = 1.0).predictionCapSeconds)
        assertEquals(2.0, ReconcileConfig(serverGuessWindowSeconds = 1.0, predictionCapSeconds = 2.0).predictionCapSeconds)
    }

    @Test
    fun aConfigOutOfOrderIsRefused() {
        assertFailsWith<IllegalArgumentException> { ReconcileConfig(correctMetres = 5f, farMetres = 4f) }
        assertFailsWith<IllegalArgumentException> { ReconcileConfig(predictionCapSeconds = 0.0) }
        assertFailsWith<IllegalArgumentException> { ReconcileConfig(trustedGuessedTicks = -1) }
    }

    /** A game's own reason: the server began holding the entity where it stood. */
    private data object Hold : SnapReason {
        override val label: String get() = "hold"
    }
}

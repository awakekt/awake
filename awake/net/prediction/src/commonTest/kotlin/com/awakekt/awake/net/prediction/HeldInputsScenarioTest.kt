/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.prediction

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A predicted entity running at 6 m/s against a minimal fake server, the scenario a game on Awake proved
 * its reconcile on: a long client frame, and the client's socket holding its inputs for 1.5 s and for
 * 3 s, then sending them all at once.
 *
 * The server ticks 30 times a second and takes one input a tick, a link's one-way trip after it was sent.
 * When an input is late it moves the entity on a guess of the last one for up to half a second, then
 * stands it: the walk of a longer hold is lost, as on a server that won't let a client run ahead of it.
 * Each snapshot (15 a second) carries the server's pose, the newest input it took, guessed ticks
 * included, and how many of those ticks it guessed, as the game's server does.
 *
 * Every frame the client fills a [ReconcileInput] from the newest snapshot and its [PredictionHistory],
 * applies [reconcile]'s answer with a [PredictedEntity], then steps.
 */
class HeldInputsScenarioTest {
    @Test
    fun aLongFrameCorrectsNothing() {
        val run = simulate(Conditions("long frame", longFrameAt = 30, holds = emptyList()), ReconcileConfig())
        assertEquals(0, run.corrections, run.summary)
        assertTrue(run.finalError <= 0.01f, run.summary)
    }

    @Test
    fun aHoldOfASecondAndAHalfNeverCorrectsWhileItLasts() {
        val run = simulate(Conditions("hold 1.5 s", holds = listOf(Hold(60, 45))), ReconcileConfig())
        assertEquals(0, run.whileHeld, run.summary)
        assertTrue(run.corrections <= 1, "at most once for the hold: ${run.summary}")
        assertEquals(0, run.farSnaps, run.summary)
        assertTrue(run.finalError <= 1.5f, run.summary)
    }

    @Test
    fun aHoldOfThreeSecondsNeverCorrectsWhileItLastsAndNeverSnaps() {
        val run = simulate(Conditions("hold 3 s", holds = listOf(Hold(60, 90))), ReconcileConfig())
        assertEquals(0, run.whileHeld, run.summary)
        assertTrue(run.corrections <= 1, "at most once for the hold: ${run.summary}")
        assertEquals(0, run.farSnaps, run.summary)
        assertTrue(run.finalError <= 1.5f, run.summary)
    }

    /**
     * All of it in one run, with the prediction cap off, at 1 s, and at its default of the server's
     * half-second guess window plus a quarter second's ease. Off, the entity runs on through a 3 s hold on
     * inputs the server stood it for, 15 m ahead, and is snapped back as the hold ends. At 1 s it runs half
     * a second past the guesses and is shifted back by that. At the default it stops about as the server
     * does, and what is left is blended.
     */
    @Test
    fun theCapKeepsAHeldEntityWithItsServer() {
        val conditions = Conditions("long frame, holds 1.5 s and 3 s", longFrameAt = 30, holds = listOf(Hold(90, 45), Hold(240, 90)))
        val off = simulate(conditions, ReconcileConfig(predictionCapSeconds = 1e9))
        val second = simulate(conditions, ReconcileConfig(predictionCapSeconds = 1.0))
        val on = simulate(conditions, ReconcileConfig())
        println("cap off:            ${off.summary}")
        println("cap 1.0 s:          ${second.summary}")
        println("cap 0.75 (default): ${on.summary}")
        assertEquals(1, off.farSnaps, "without the cap the 3 s hold ends in a snap: ${off.summary}")
        assertEquals(0, second.farSnaps, second.summary)
        assertEquals(0, on.farSnaps, on.summary)
        assertEquals(0, on.whileHeld, on.summary)
        assertEquals(0, on.corrections, "the default cap leaves only a blend: ${on.summary}")
        assertTrue(on.largestBlend <= 1f, "about its ease's walk, under the correct distance: ${on.summary}")
        assertTrue(second.largestCorrection < off.largestCorrection, second.summary)
        assertTrue(on.finalError <= 1.5f, on.summary)
    }

    /** The client's socket sends nothing from tick [start] for [ticks] ticks, then all it held at once. */
    private class Hold(val start: Int, val ticks: Int)

    /** A run's conditions: a frame of [LONG_FRAME_TICKS] ticks at [longFrameAt] (none when -1), and [holds]. */
    private class Conditions(val name: String, val longFrameAt: Int = -1, val holds: List<Hold>)

    private class Snapshot(val arrives: Int, val ack: Long, val guessed: Int, val x: Float)

    private class Input(val arrives: Int, val seq: Long, val v: Float)

    /** The fake authoritative server: moves the entity along x by the velocity each input asks for. */
    private class FakeServer {
        var x = 0f
        var ack = 0L
        var guessRun = 0
        private var started = false
        private var nextSeq = 1L
        private var lastV = 0f
        private val received = HashMap<Long, Float>()
        private val guessed = HashMap<Long, Float>()

        fun tick(inputs: MutableList<Input>, now: Int) {
            val arrived = inputs.filter { it.arrives <= now }
            inputs.removeAll(arrived)
            arrived.forEach(::take)
            if (started) step()
        }

        private fun take(input: Input) {
            started = true
            if (input.seq >= nextSeq) {
                received[input.seq] = input.v
                return
            }
            // A late input whose tick has gone: a guess is put right, a stood tick's walk is lost.
            guessed.remove(input.seq)?.let { guess -> x += (input.v - guess) * DT }
            ack = maxOf(ack, input.seq)
            lastV = input.v
        }

        private fun step() {
            val v = received.remove(nextSeq)
            if (v != null) {
                x += v * DT
                lastV = v
                ack = nextSeq
                guessRun = 0
            } else if (guessRun < GUESSED_TICKS) {
                // A guess: acknowledged as taken, and counted as guessed.
                x += lastV * DT
                guessed[nextSeq] = lastV
                ack = nextSeq
                guessRun++
            }
            nextSeq++
        }
    }

    /** The client: reconciles each frame with the newest snapshot, then predicts and sends its inputs. */
    private class Client(private val conditions: Conditions, private val config: ReconcileConfig) {
        val history = PredictionHistory()
        val entity = PredictedEntity()
        val input = ReconcileInput()
        val toServer = ArrayList<Input>()
        val toClient = ArrayList<Snapshot>()
        private var latest: Snapshot? = null
        private var previousAck = 0L
        private var fresh = false
        private var readAck = 0L
        private var seq = 0L
        private var missed = 0
        var corrections = 0
        var whileHeld = 0
        var farSnaps = 0
        var largestCorrection = 0f
        var largestBlend = 0f
        var finalError = Float.NaN
        var summary = ""

        fun frame(tick: Int) {
            if (tick >= conditions.longFrameAt && tick < conditions.longFrameAt + LONG_FRAME_TICKS) {
                missed++
                return
            }
            val now = tick * DT.toDouble()
            receive(tick)
            latest?.let { reconcileWith(it, tick, now) }
            // This frame's step, and those a long frame missed, each an input of its own sent now.
            val release = conditions.holds.firstOrNull { tick >= it.start && tick < it.start + it.ticks }?.let { it.start + it.ticks }
            repeat(missed + 1) {
                val v = SPEED * entity.speedScale
                entity.position.x += v * DT
                entity.blend(abs(v) * DT, history)
                history.record(++seq, entity.position, now)
                toServer += Input(arrives = (release ?: tick) + LINK_TICKS, seq = seq, v = v)
            }
            missed = 0
            entity.update(DT)
        }

        /** The snapshots that arrived since the last frame, the newest last. */
        private fun receive(tick: Int) {
            for (snapshot in toClient.filter { it.arrives <= tick }) {
                fresh = snapshot.ack > previousAck
                previousAck = snapshot.ack
                latest = snapshot
            }
            toClient.removeAll { it.arrives <= tick }
        }

        private fun reconcileWith(snapshot: Snapshot, tick: Int, now: Double) {
            input.server(snapshot.x, 0f, 0f).drawn(entity.position.x, entity.position.y, entity.position.z)
            input.ackSeq = snapshot.ack
            input.ackFresh = fresh
            input.ackUnread = snapshot.ack > readAck
            input.guessedTicks = snapshot.guessed
            input.lookUp(history, now)
            val correction = reconcile(input, config)
            if (correction is Correction.Blend || correction.corrects) readAck = snapshot.ack
            if (correction.corrects) count(correction, tick)
            if (correction is Correction.Blend) largestBlend = maxOf(largestBlend, abs(correction.dx))
            entity.apply(correction, history)
        }

        private fun count(correction: Correction, tick: Int) {
            corrections++
            if (conditions.holds.any { tick >= it.start && tick <= it.start + it.ticks + LINK_TICKS }) whileHeld++
            val distance = when (correction) {
                is Correction.Shift -> abs(correction.dx)
                is Correction.Snap -> abs(correction.x - entity.position.x)
                else -> 0f
            }
            largestCorrection = maxOf(largestCorrection, distance)
            if (correction is Correction.Snap && correction.reason == SnapReason.Far) farSnaps++
        }
    }

    private fun simulate(conditions: Conditions, config: ReconcileConfig): Client {
        val server = FakeServer()
        val client = Client(conditions, config)
        for (tick in 0 until RUN_TICKS) {
            client.frame(tick)
            server.tick(client.toServer, tick)
            if (tick % SNAPSHOT_EVERY == 0) client.toClient += Snapshot(tick + LINK_TICKS, server.ack, server.guessRun, server.x)
        }
        // The last frame's look: the server's pose against the prediction's, at the input it acknowledged.
        client.finalError = if (client.input.ackKnown) client.input.ackDrift() else Float.NaN
        client.summary = "${conditions.name}: corrections=${client.corrections} whileHeld=${client.whileHeld} " +
            "farSnaps=${client.farSnaps} largestCorrection=${metres(client.largestCorrection)} " +
            "largestBlend=${metres(client.largestBlend)} finalError=${metres(client.finalError)}"
        return client
    }

    private fun metres(value: Float): String = if (value.isNaN()) "NaN" else "${(value * 100).roundToInt() / 100.0} m"

    private companion object {
        /** 30 ticks and inputs a second, a snapshot every other tick, and a one-way trip of two ticks. */
        const val DT = 1f / 30f
        const val SNAPSHOT_EVERY = 2
        const val LINK_TICKS = 2

        /** A run speed, in metres a second. */
        const val SPEED = 6f

        /** Half a second of ticks the server moves a late input on a guess before it stands the entity. */
        const val GUESSED_TICKS = 15

        /** A 267 ms frame. */
        const val LONG_FRAME_TICKS = 8

        /** Thirteen seconds. */
        const val RUN_TICKS = 390
    }
}

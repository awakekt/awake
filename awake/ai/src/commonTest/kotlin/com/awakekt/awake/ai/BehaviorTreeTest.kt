/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import kotlin.test.Test
import kotlin.test.assertEquals

class BehaviorTreeTest {

    private fun testContext(): AiContext =
        AiContext(
            world = World(),
            entity = Entity.of(1, 0),
            delta = 0.016f,
            blackboard = Blackboard(),
        )

    @Test
    fun sequenceExecutesUntilFailure() {
        var step1 = false
        var step2 = false
        var step3 = false

        val tree = behaviorTree {
            sequence {
                action {
                    step1 = true
                    BehaviorStatus.SUCCESS
                }
                action {
                    step2 = true
                    BehaviorStatus.FAILURE
                }
                action {
                    step3 = true
                    BehaviorStatus.SUCCESS
                }
            }
        }

        val status = tree.tick(testContext())
        assertEquals(BehaviorStatus.FAILURE, status)
        assertEquals(true, step1)
        assertEquals(true, step2)
        assertEquals(false, step3, "Step 3 should not run after Step 2 failed")
    }

    @Test
    fun sequenceSucceedsWhenAllSucceed() {
        var count = 0
        val tree = behaviorTree {
            sequence {
                action {
                    count++
                    BehaviorStatus.SUCCESS
                }
                action {
                    count++
                    BehaviorStatus.SUCCESS
                }
            }
        }

        val status = tree.tick(testContext())
        assertEquals(BehaviorStatus.SUCCESS, status)
        assertEquals(2, count)
    }

    @Test
    fun selectorReturnsFirstSuccess() {
        var ranFirst = false
        var ranSecond = false

        val tree = behaviorTree {
            selector {
                action {
                    ranFirst = true
                    BehaviorStatus.SUCCESS
                }
                action {
                    ranSecond = true
                    BehaviorStatus.SUCCESS
                }
            }
        }

        val status = tree.tick(testContext())
        assertEquals(BehaviorStatus.SUCCESS, status)
        assertEquals(true, ranFirst)
        assertEquals(false, ranSecond, "Second fallback should not execute if first succeeded")
    }

    @Test
    fun selectorFallsBackOnFailure() {
        var ranFirst = false
        var ranSecond = false

        val tree = behaviorTree {
            selector {
                action {
                    ranFirst = true
                    BehaviorStatus.FAILURE
                }
                action {
                    ranSecond = true
                    BehaviorStatus.SUCCESS
                }
            }
        }

        val status = tree.tick(testContext())
        assertEquals(BehaviorStatus.SUCCESS, status)
        assertEquals(true, ranFirst)
        assertEquals(true, ranSecond)
    }

    @Test
    fun inverterFlipsStatus() {
        val invertedFail = InverterNode(ActionNode { BehaviorStatus.FAILURE })
        val invertedSuccess = InverterNode(ActionNode { BehaviorStatus.SUCCESS })
        val invertedRunning = InverterNode(ActionNode { BehaviorStatus.RUNNING })

        val ctx = testContext()
        assertEquals(BehaviorStatus.SUCCESS, invertedFail.tick(ctx))
        assertEquals(BehaviorStatus.FAILURE, invertedSuccess.tick(ctx))
        assertEquals(BehaviorStatus.RUNNING, invertedRunning.tick(ctx))
    }

    @Test
    fun repeaterExecutesFixedTimes() {
        var runs = 0
        val repeater = RepeaterNode(
            ActionNode {
                runs++
                BehaviorStatus.SUCCESS
            },
            count = 3,
        )
        val ctx = testContext()

        assertEquals(BehaviorStatus.SUCCESS, repeater.tick(ctx))
        assertEquals(3, runs)
    }

    @Test
    fun cooldownPreventsImmediateReexecution() {
        var runs = 0
        val node = CooldownNode(
            ActionNode {
                runs++
                BehaviorStatus.SUCCESS
            },
            cooldownSeconds = 1.0f,
        )
        val ctx = testContext()

        assertEquals(BehaviorStatus.SUCCESS, node.tick(ctx))
        assertEquals(1, runs)

        // Second tick immediately should fail due to cooldown
        assertEquals(BehaviorStatus.FAILURE, node.tick(ctx))
        assertEquals(1, runs)

        // Advance past cooldown
        ctx.delta = 1.1f
        assertEquals(BehaviorStatus.FAILURE, node.tick(ctx)) // timer depleted
        assertEquals(BehaviorStatus.SUCCESS, node.tick(ctx)) // runs again
        assertEquals(2, runs)
    }

    @Test
    fun conditionNodeGatesBranch() {
        var allowed = false
        val tree = behaviorTree {
            sequence {
                condition { allowed }
                action { BehaviorStatus.SUCCESS }
            }
        }

        val ctx = testContext()
        assertEquals(BehaviorStatus.FAILURE, tree.tick(ctx))

        allowed = true
        assertEquals(BehaviorStatus.SUCCESS, tree.tick(ctx))
    }
}

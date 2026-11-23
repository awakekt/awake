/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.state

import com.awakekt.awake.core.state.DefaultReducerStore
import com.awakekt.awake.core.state.Reducer

internal class UiShowcaseCounterStore :
    DefaultReducerStore<
        UiShowcaseCounterContract.State,
        UiShowcaseCounterContract.Intent,
        UiShowcaseCounterContract.Effect,
        >(
        initialState = UiShowcaseCounterContract.State(),
        reducer = Reducer { state, intent ->
            when (intent) {
                UiShowcaseCounterContract.Intent.Increment -> {
                    val next = state.copy(count = state.count + 1)
                    val effect = if (next.count > 0 && next.count % 5 == 0) {
                        UiShowcaseCounterContract.Effect.MilestoneReached(next.count)
                    } else {
                        null
                    }
                    next to effect
                }

                UiShowcaseCounterContract.Intent.Decrement ->
                    state.copy(count = state.count - 1) to null

                UiShowcaseCounterContract.Intent.Reset -> {
                    val effect = if (state.count != 0) UiShowcaseCounterContract.Effect.ResetCompleted else null
                    UiShowcaseCounterContract.State() to effect
                }
            }
        },
    )

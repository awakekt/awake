/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.sample.composeshowcase.ui.pages

import com.awakekt.awake.compose.di.ProvideContainer
import com.awakekt.awake.compose.di.rememberResolve
import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.clickable
import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.state.rememberReducerStore
import com.awakekt.awake.compose.state.rememberStore
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.di.container
import com.awakekt.awake.core.di.module
import com.awakekt.awake.core.text.theme.TextStyle
import com.awakekt.awake.sample.composeshowcase.ui.ShowcaseCategory
import com.awakekt.awake.sample.composeshowcase.ui.ShowcasePage

private data class CounterState(
    val count: Int = 0,
    val status: String = "Ready",
)

private data class TaskState(
    val tasks: List<String> = listOf("Verify Vulkan backend", "Inspect ECS scene runtime", "Test DI injection"),
    val lastAction: String = "Initialized",
)

private sealed interface TaskIntent {
    data class AddTask(val name: String) : TaskIntent
    data class RemoveTask(val index: Int) : TaskIntent
    data object ClearTasks : TaskIntent
}

private class ShowcaseService(val tag: String = "EngineService") {
    fun formatMessage(msg: String): String = "[$tag] $msg"
}

internal val StateDiPage = ShowcasePage(
    id = "state-di",
    title = "State & DI",
    category = ShowcaseCategory.StateDi,
    description = "Integration of :awake:compose:state (Store & ReducerStore) and :awake:compose:di (ambient Container & rememberResolve).",
    notes = listOf(
        "rememberStore: Lightweight mutable store with atomic updates (Zustand / StateHolder equivalent).",
        "rememberReducerStore: Pure Reducer transition store enforcing Unidirectional Data Flow (MVI / Redux equivalent).",
        "ProvideContainer & rememberResolve: Ambient dependency injection down the Compose hierarchy.",
    ),
    demo = {
        val sampleContainer = remember {
            container(
                module {
                    singleton { ShowcaseService("AwakeDI") }
                },
            )
        }

        ProvideContainer(sampleContainer) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Section 1: rememberStore (Counter)
                DemoSectionLabel("1. rememberStore (Reactive StateHolder)")
                DemoCounterSection()

                Spacer(Modifier.height(8.dp))

                // Section 2: rememberReducerStore (MVI Task List)
                DemoSectionLabel("2. rememberReducerStore (MVI / UDF Reducer)")
                DemoTasksSection()

                Spacer(Modifier.height(8.dp))

                // Section 3: Ambient DI (rememberResolve)
                DemoSectionLabel("3. :awake:compose:di (Ambient Container & Resolution)")
                DemoDiSection()
            }
        }
    },
)

context(composer: Composer)
private fun DemoCounterSection() {
    val counterStore = rememberStore("demo_counter") { CounterState() }
    val state = counterStore.value

    Row(
        horizontalArrangement = Arrangement.spacedByHorizontal(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(140.dp).height(44.dp).background(Color(0.2f, 0.2f, 0.25f, 1f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("Count: ${state.count} (${state.status})", style = TextStyle.Default.copy(color = Color.White))
        }

        Row(horizontalArrangement = Arrangement.spacedByHorizontal(8.dp)) {
            DemoButton("+1") {
                counterStore.update { it.copy(count = it.count + 1, status = "Incremented") }
            }
            DemoButton("-1") {
                counterStore.update { it.copy(count = it.count - 1, status = "Decremented") }
            }
            DemoButton("Reset") {
                counterStore.update { it.copy(count = 0, status = "Reset") }
            }
        }
    }
}

context(composer: Composer)
private fun DemoTasksSection() {
    val taskStore = rememberReducerStore<TaskState, TaskIntent, Nothing>(
        key = "demo_tasks",
        initialState = { TaskState() },
    ) { state, intent ->
        when (intent) {
            is TaskIntent.AddTask -> state.copy(
                tasks = state.tasks + intent.name,
                lastAction = "Added '${intent.name}'",
            ) to null

            is TaskIntent.RemoveTask -> {
                val updated = state.tasks.toMutableList()
                if (intent.index in updated.indices) {
                    val removed = updated.removeAt(intent.index)
                    state.copy(tasks = updated, lastAction = "Removed '$removed'") to null
                } else {
                    state to null
                }
            }

            TaskIntent.ClearTasks -> state.copy(
                tasks = emptyList(),
                lastAction = "Cleared all tasks",
            ) to null
        }
    }
    val state = taskStore.value

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedByHorizontal(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Action: ${state.lastAction}", style = TextStyle.Default.copy(color = Color(0.4f, 0.4f, 0.45f, 1f)))
            DemoButton("+ Add Task") {
                taskStore.dispatch(TaskIntent.AddTask("Task #${state.tasks.size + 1}"))
            }
            DemoButton("Clear") {
                taskStore.dispatch(TaskIntent.ClearTasks)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedByHorizontal(8.dp)) {
            state.tasks.forEachIndexed { index, task ->
                Row(
                    modifier = Modifier
                        .background(Color(0.88f, 0.90f, 0.95f, 1f))
                        .clickable { taskStore.dispatch(TaskIntent.RemoveTask(index)) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("$task  ×", style = TextStyle.Default.copy(color = Color(0.1f, 0.15f, 0.3f, 1f)))
                }
            }
            if (state.tasks.isEmpty()) {
                Text("(No active tasks)", style = TextStyle.Default.copy(color = Color(0.6f, 0.6f, 0.6f, 1f)))
            }
        }
    }
}

context(composer: Composer)
private fun DemoDiSection() {
    val service = rememberResolve<ShowcaseService>()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0.92f, 0.95f, 0.92f, 1f))
            .padding(12.dp),
    ) {
        Text(
            service.formatMessage("Successfully resolved from ambient LocalContainer via rememberResolve()!"),
            style = TextStyle.Default.copy(color = Color(0.1f, 0.4f, 0.15f, 1f)),
        )
    }
}

context(composer: Composer)
private fun DemoButton(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .background(Color(0.25f, 0.45f, 0.85f, 1f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = TextStyle.Default.copy(color = Color.White))
    }
}

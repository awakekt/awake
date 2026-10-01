/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.bootstrap.dsl

import com.awakekt.awake.engine.platform.core.AppModule
import com.awakekt.awake.engine.platform.core.AppSpec
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle

/**
 * Defines a parameterized application template with explicit [State] creation.
 *
 * @param State The application state type.
 * @param createState A factory producing the initial application state.
 * @param block The configuration lambda for configuring window and module factories.
 * @return An [AppDefinition] template capable of instantiating the application.
 */
@Deprecated("Use app { module(...) } directly instead.", ReplaceWith("app"))
fun <State> appDefinition(
    createState: () -> State,
    block: AppDefinitionDsl<State>.() -> Unit,
): AppDefinition<State> = AppDefinitionDsl(createState).apply(block).build()

/**
 * A compiled application definition that pairs state creation with window and module factories.
 *
 * @param State The application state type.
 * @param createStateBlock Factory block producing the initial application state.
 * @param windowBlock Configuration lambda for the application window.
 * @param moduleFactory Factory constructing the [AppModule] for the given state.
 */
class AppDefinition<State> internal constructor(
    private val createStateBlock: () -> State,
    private val windowBlock: WindowDsl.() -> Unit,
    private val moduleFactory: (State) -> AppModule,
) {
    /**
     * Instantiates a fresh [State] instance.
     *
     * @return The freshly created application state.
     */
    fun createState(): State = createStateBlock()

    /**
     * Builds an [AppModule] bound to the given application [state].
     *
     * @param state The state instance to pass to the module factory.
     * @return An [AppModule] configured with [state].
     */
    fun createModule(state: State): AppModule = moduleFactory(state)

    /**
     * Creates an [AwakeAppLifecycle] using a freshly created state.
     *
     * @return An initialized [AwakeAppLifecycle] instance.
     */
    fun createApp(): AwakeAppLifecycle = createApp(createState())

    /**
     * Creates an [AwakeAppLifecycle] with the provided explicit [state].
     *
     * @param state The state instance to initialize the application with.
     * @return An initialized [AwakeAppLifecycle] instance.
     */
    fun createApp(state: State): AwakeAppLifecycle = createAppSpec(state).createLifecycle()

    /**
     * Creates an [AppSpec] using a freshly created state.
     *
     * @return The compiled [AppSpec].
     */
    fun createAppSpec(): AppSpec = createAppSpec(createState())

    /**
     * Creates an [AppSpec] using the provided explicit [state].
     *
     * @param state The state instance to initialize the application spec with.
     * @return The compiled [AppSpec].
     */
    fun createAppSpec(state: State): AppSpec = createModule(state).createAppSpec(windowBlock)
}

/**
 * Builder DSL scope for constructing an [AppDefinition].
 *
 * @param State The application state type.
 * @param createStateBlock Factory block producing the initial application state.
 */
@AwakeAppDsl
class AppDefinitionDsl<State> internal constructor(
    private val createStateBlock: () -> State,
) {
    private var windowBlock: WindowDsl.() -> Unit = {}
    private var moduleFactory: ((State) -> AppModule)? = null

    /**
     * Configures the application window settings.
     *
     * @param block The window configuration lambda.
     */
    fun window(block: WindowDsl.() -> Unit) {
        windowBlock = block
    }

    /**
     * Configures a module factory taking the application state.
     *
     * @param factory The module factory lambda.
     */
    fun module(factory: (State) -> AppModule) {
        moduleFactory = factory
    }

    /**
     * Configures a constant module instance for this application definition.
     *
     * @param module The module instance to use.
     */
    fun module(module: AppModule) {
        moduleFactory = { module }
    }

    internal fun build(): AppDefinition<State> {
        val builtModule = checkNotNull(moduleFactory) {
            "gameDefinition requires a module { ... } factory or module(instance)."
        }
        return AppDefinition(
            createStateBlock = createStateBlock,
            windowBlock = windowBlock,
            moduleFactory = builtModule,
        )
    }
}

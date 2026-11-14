/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.settings

import com.awakekt.awake.core.config.LaunchOption
import com.awakekt.awake.core.config.ValueType

/**
 * One player setting, declared once in a [SettingsSchema]. Its value resolves in one order, for every reader:
 * [default], then the player's saved value, then the launch option [override] when it was given.
 *
 * @param T the type of the value.
 * @property name the key it is saved under, such as `graphics.shadowDistance`.
 * @property type how the value is written to storage and read back: the same codec a launch option uses.
 * @property default the value before the player changes it. Only values that differ from it are saved, so a
 *   new default reaches every player who never changed the setting.
 * @property label the name a settings window shows.
 * @property description a longer explanation, for a settings window.
 * @property min the smallest allowed number, for a slider, or null.
 * @property max the largest allowed number, for a slider, or null.
 * @property choices the allowed values as text, for a picker, or empty.
 * @property override the launch option that wins over the saved value for this session, or null.
 * @property formerly names the setting was saved under before; read once and saved under [name].
 * @param check why a value is refused, or null when it is allowed.
 * @property index the setting's place in its schema.
 */
class SettingKey<T : Any> internal constructor(
    val name: String,
    val type: ValueType<T>,
    val default: T,
    val label: String,
    val description: String,
    val min: Double?,
    val max: Double?,
    val choices: List<String>,
    val override: LaunchOption<T>?,
    val formerly: List<String>,
    private val check: ((T) -> String?)?,
    internal val index: Int,
) {
    /** Why [value] is refused, or null when it is allowed. */
    fun refusal(value: T): String? = check?.invoke(value)

    override fun toString(): String = "SettingKey($name)"
}

/**
 * The settings a game has, each declared once, as a plain Kotlin object:
 *
 * ```kotlin
 * object GameSettings : SettingsSchema() {
 *     val touch = boolean("input.touch", default = isTouchFirst, label = "Touch controls", override = Launch.touch)
 *     val volume = float("audio.master", 0.8f, 0f..1f, label = "Master volume")
 * }
 * ```
 *
 * [keys] lists every setting with its label, range and choices, so a settings window can be laid out from it.
 */
abstract class SettingsSchema {
    private val declared = ArrayList<SettingKey<*>>()

    /** Every setting, in the order it was declared. */
    val keys: List<SettingKey<*>> get() = declared

    /** Declares a setting of any [type]; [check] returns why a value is refused. */
    @Suppress("LongParameterList")
    protected fun <T : Any> setting(
        name: String,
        type: ValueType<T>,
        default: T,
        label: String = name,
        description: String = "",
        override: LaunchOption<T>? = null,
        formerly: List<String> = emptyList(),
        min: Double? = null,
        max: Double? = null,
        choices: List<String> = emptyList(),
        check: ((T) -> String?)? = null,
    ): SettingKey<T> {
        val taken = declared.map { it.name } + declared.flatMap { it.formerly }
        for (each in listOf(name) + formerly) require(each !in taken) { "Setting '$each' is declared twice" }
        require(check?.invoke(default) == null) { "The default of setting '$name' is refused by its own check" }
        val key = SettingKey(
            name, type, default, label, description, min, max, choices, override, formerly, check, declared.size,
        )
        declared += key
        return key
    }

    /** An on-off setting. */
    protected fun boolean(
        name: String,
        default: Boolean,
        label: String = name,
        description: String = "",
        override: LaunchOption<Boolean>? = null,
        formerly: List<String> = emptyList(),
    ): SettingKey<Boolean> = setting(name, ValueType.Boolean, default, label, description, override, formerly)

    /** A whole number within [range]. */
    @Suppress("LongParameterList")
    protected fun int(
        name: String,
        default: Int,
        range: IntRange,
        label: String = name,
        description: String = "",
        override: LaunchOption<Int>? = null,
        formerly: List<String> = emptyList(),
    ): SettingKey<Int> = setting(
        name, ValueType.Int, default, label, description, override, formerly,
        min = range.first.toDouble(), max = range.last.toDouble(),
        check = { if (it in range) null else "is outside $range" },
    )

    /** A number within [range]. */
    @Suppress("LongParameterList")
    protected fun float(
        name: String,
        default: Float,
        range: ClosedFloatingPointRange<Float>,
        label: String = name,
        description: String = "",
        override: LaunchOption<Float>? = null,
        formerly: List<String> = emptyList(),
    ): SettingKey<Float> = setting(
        name, ValueType.Float, default, label, description, override, formerly,
        min = range.start.toDouble(), max = range.endInclusive.toDouble(),
        check = { if (it in range) null else "is outside $range" },
    )

    /** Text, such as a name; [check] returns why a value is refused. */
    @Suppress("LongParameterList")
    protected fun string(
        name: String,
        default: String,
        label: String = name,
        description: String = "",
        override: LaunchOption<String>? = null,
        formerly: List<String> = emptyList(),
        check: ((String) -> String?)? = null,
    ): SettingKey<String> = setting(name, ValueType.String, default, label, description, override, formerly, check = check)

    /** One of [entries]. */
    @Suppress("LongParameterList")
    protected fun <E : Enum<E>> choice(
        name: String,
        default: E,
        entries: List<E>,
        label: String = name,
        description: String = "",
        override: LaunchOption<E>? = null,
        formerly: List<String> = emptyList(),
    ): SettingKey<E> {
        val type = ValueType.choice(entries)
        return setting(
            name,
            type,
            default,
            label,
            description,
            override,
            formerly,
            choices = entries.map(type::format),
        )
    }
}

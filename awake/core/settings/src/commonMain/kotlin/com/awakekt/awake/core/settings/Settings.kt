/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.settings

import com.awakekt.awake.core.config.LaunchValues
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The player's settings of a [SettingsSchema]: each value resolved once, observable, and saved when it changes.
 *
 * Reading a value allocates nothing. [state] is a `StateFlow`, so a UI on the engine's Compose runtime binds
 * one with `collectAsState()` and updates when it changes. A change is applied at once and written after
 * [saveDelayMillis], coalesced with the changes that follow it, in [scope]: never on the caller's frame. Only
 * values that differ from their default are written, and names the schema does not know are kept, so a newer
 * build's settings survive a run of an older one. Nothing here throws: [problems] lists what was refused or
 * failed. Change settings from one thread, the one that reads them.
 */
@Suppress("TooManyFunctions") // One store: reads, changes, the save queue and the load that resolves each key.
class Settings private constructor(
    /** The schema these settings are of. */
    val schema: SettingsSchema,
    private val storage: SettingsStorage,
    private val scope: CoroutineScope,
    private val saveDelayMillis: Long,
) {
    private val states = Array(schema.keys.size) { MutableStateFlow<Any>(schema.keys[it].default) }
    private val overridden = BooleanArray(schema.keys.size)
    private val saved = LinkedHashMap<String, String>()
    private val reported = MutableStateFlow<List<SettingsProblem>>(emptyList())
    private var pending: String? = null
    private var saving: Job? = null

    /** What was refused or failed since [open], in order. */
    val problems: StateFlow<List<SettingsProblem>> = reported.asStateFlow()

    /** The value of [key]. */
    operator fun <T : Any> get(key: SettingKey<T>): T {
        @Suppress("UNCHECKED_CAST")
        return slot(key).value as T
    }

    /** [key]'s value as it changes. */
    fun <T : Any> state(key: SettingKey<T>): StateFlow<T> {
        @Suppress("UNCHECKED_CAST")
        return slot(key) as StateFlow<T>
    }

    /** Whether [key]'s launch option decided its value for this session, and the player has not changed it since. */
    fun isOverridden(key: SettingKey<*>): Boolean {
        slot(key)
        return overridden[key.index]
    }

    /** Whether the player's own value of [key] is saved, rather than the default. */
    fun isSaved(key: SettingKey<*>): Boolean {
        slot(key)
        return key.name in saved
    }

    /**
     * Sets [key] to [value] and saves it, ending a launch override for this session. A value the key refuses is
     * reported and returns false. Setting the default removes the saved value.
     */
    operator fun <T : Any> set(key: SettingKey<T>, value: T): Boolean {
        val reason = key.refusal(value)
        if (reason != null) {
            report(SettingsProblem.Invalid(key, key.type.format(value), reason))
            return false
        }
        slot(key).value = value
        overridden[key.index] = false
        if (value == key.default) saved.remove(key.name) else saved[key.name] = key.type.format(value)
        scheduleSave()
        return true
    }

    /** Sets [key] from [text], as a generated settings window does; false when the text is refused. */
    fun setText(key: SettingKey<*>, text: String): Boolean = setParsed(key, text)

    /** [key]'s value as text, as [setText] reads it. */
    fun text(key: SettingKey<*>): String = formatValue(key)

    /** Puts [key] back to its default. */
    fun <T : Any> reset(key: SettingKey<T>): Boolean = set(key, key.default)

    /** Writes a pending change now, as a game does when it closes. A failure is reported, not thrown. */
    suspend fun flush() {
        saving?.cancelAndJoin()
        writePending()
    }

    private fun slot(key: SettingKey<*>): MutableStateFlow<Any> {
        require(schema.keys.getOrNull(key.index) === key) { "$key is not a setting of this schema" }
        return states[key.index]
    }

    private fun <T : Any> setParsed(key: SettingKey<T>, text: String): Boolean {
        val value = key.type.parse(text)
        if (value == null) report(SettingsProblem.Invalid(key, text, "is not a ${key.type.name}"))
        return value != null && set(key, value)
    }

    private fun <T : Any> formatValue(key: SettingKey<T>): String = key.type.format(get(key))

    private fun scheduleSave() {
        pending = JsonObject(saved.mapValues { JsonPrimitive(it.value) }).toString()
        if (saving?.isActive == true) return
        saving = scope.launch {
            delay(saveDelayMillis)
            writePending()
        }
    }

    private suspend fun writePending() {
        val text = pending ?: return
        pending = null
        try {
            storage.write(text)
        } catch (cancelled: CancellationException) {
            if (pending == null) pending = text
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            report(SettingsProblem.StorageFailed(failure))
        }
    }

    private fun report(problem: SettingsProblem) = reported.update { it + problem }

    private suspend fun load(launch: LaunchValues?) {
        val text = try {
            storage.read()
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            report(SettingsProblem.StorageFailed(failure))
            null
        }
        if (text != null) readSaved(text)
        var renamed = false
        for (key in schema.keys) {
            val former = key.formerly.firstOrNull { it in saved }
            if (former != null && key.name !in saved) saved[key.name] = saved.getValue(former)
            key.formerly.forEach { renamed = saved.remove(it) != null || renamed }
            saved[key.name]?.let { if (!restore(key, it)) saved.remove(key.name) }
            if (launch != null) applyOverride(key, launch)
        }
        if (renamed) scheduleSave()
    }

    private suspend fun readSaved(text: String) {
        val values = try {
            Json.parseToJsonElement(text).jsonObject.mapValues { it.value.jsonPrimitive.content }
        } catch (@Suppress("TooGenericExceptionCaught") unreadable: Exception) {
            report(SettingsProblem.Unreadable(unreadable.message ?: "not a JSON object"))
            try {
                storage.keepAside(text)
            } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
                report(SettingsProblem.StorageFailed(failure))
            }
            return
        }
        saved.putAll(values)
    }

    private fun <T : Any> restore(key: SettingKey<T>, text: String): Boolean {
        val value = key.type.parse(text)
        val reason = if (value == null) "is not a ${key.type.name}" else key.refusal(value)
        if (reason != null || value == null) {
            report(SettingsProblem.Invalid(key, text, reason ?: "is not a ${key.type.name}"))
            return false
        }
        states[key.index].value = value
        return true
    }

    private fun <T : Any> applyOverride(key: SettingKey<T>, launch: LaunchValues) {
        val option = key.override
        if (option == null || option !in launch.schema.options || !launch.isGiven(option)) return
        val value = launch[option]
        val reason = key.refusal(value)
        if (reason != null) {
            report(SettingsProblem.Invalid(key, key.type.format(value), reason))
        } else {
            states[key.index].value = value
            overridden[key.index] = true
        }
    }

    /** Opening settings. */
    companion object {
        /** How long a change waits for the changes after it before they are written together. */
        const val DEFAULT_SAVE_DELAY_MILLIS: Long = 500

        /**
         * Reads [schema]'s saved values from [storage] and applies the overrides [launch] was given. Unreadable
         * text is kept aside and every setting starts from its default. Writes run in [scope].
         */
        suspend fun open(
            schema: SettingsSchema,
            storage: SettingsStorage,
            scope: CoroutineScope,
            launch: LaunchValues? = null,
            saveDelayMillis: Long = DEFAULT_SAVE_DELAY_MILLIS,
        ): Settings = Settings(schema, storage, scope, saveDelayMillis).also { it.load(launch) }
    }
}

/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.cli

import com.awakekt.awake.project.AwakeProjectManifest
import com.awakekt.awake.project.runtime.PROJECT_MANIFEST
import com.awakekt.awake.project.runtime.SceneCapability
import java.io.File
import java.lang.ref.WeakReference
import java.lang.reflect.Modifier
import java.net.URLClassLoader

/**
 * A project's own built code, the class folders and jars on [classpath], in a class loader of its own
 * whose parent holds Core, as an editor loads a project's code: Core's types, [SceneCapability] among
 * them, are the ones `awake` runs with, and the project's are its own. [close] it after the check.
 */
internal class ProjectCode(private val classpath: List<File>) : AutoCloseable {
    private var loader: URLClassLoader? = URLClassLoader(
        "awake-project-code",
        classpath.map { it.toURI().toURL() }.toTypedArray(),
        SceneCapability::class.java.classLoader,
    )

    // Whether the loader has been collected, which only a weak reference can tell.
    private val collectable = WeakReference(loader)

    /** The capabilities [manifest]'s plugins name, and what kept any of them from loading. */
    fun capabilities(manifest: AwakeProjectManifest): Pair<List<SceneCapability>, List<Finding>> {
        val findings = ArrayList<Finding>()
        val capabilities = manifest.plugins.mapIndexedNotNull { index, plugin ->
            val name = plugin.capabilityClass
            if (name == null) {
                if (plugin.required) findings += error("plugins[$index] ${plugin.id} is required but names no capabilityClass to load")
                return@mapIndexedNotNull null
            }
            when (val loaded = load(name)) {
                is Loaded.Refused -> {
                    findings += error("plugins[$index].capabilityClass $name ${loaded.why}")
                    null
                }
                is Loaded.Capability -> loaded.capability.takeIf { it.id == plugin.id }
                    ?: null.also { findings += error("plugins[$index].capabilityClass $name has the id '${loaded.capability.id}', not '${plugin.id}'") }
            }
        }
        return capabilities to findings
    }

    // TooGenericExceptionCaught: a capability's initializer may throw anything, which is a finding, not a crash.
    @Suppress("TooGenericExceptionCaught")
    private fun load(name: String): Loaded = try {
        val type = Class.forName(name, true, checkNotNull(loader) { "The project's code is closed" })
        if (SceneCapability::class.java.isAssignableFrom(type)) Loaded.Capability(instance(type) as SceneCapability) else Loaded.Refused("is not a SceneCapability")
    } catch (missing: ClassNotFoundException) {
        Loaded.Refused("is not in the project's code (${classpath.joinToString(File.pathSeparator)}): ${missing.message}")
    } catch (failure: LinkageError) {
        Loaded.Refused("can't be loaded: $failure")
    } catch (constructor: NoSuchMethodException) {
        Loaded.Refused("is neither an object nor a class with a public constructor that takes nothing (${constructor.message})")
    } catch (failure: Exception) {
        Loaded.Refused("can't be made: ${failure.cause ?: failure}")
    }

    /** A Kotlin `object`'s instance, or a new one made with the class's public constructor that takes nothing. */
    private fun instance(type: Class<*>): Any {
        val singleton = type.declaredFields.firstOrNull { it.name == "INSTANCE" && Modifier.isStatic(it.modifiers) && it.type == type }
        return singleton?.get(null) ?: type.getConstructor().newInstance()
    }

    /**
     * Whether the class loader has been collected, once this is closed and nothing of the check holds
     * the project's code. One that isn't, after a few collections, is held by something outside the
     * project, such as a process-wide registry, a cache or a thread, which would hold every project an
     * editor opens. Collects garbage while it waits.
     */
    fun isCollected(): Boolean {
        check(loader == null) { "Close the project's code first" }
        repeat(COLLECTIONS) {
            if (collectable.get() == null) return true
            System.gc()
            Thread.sleep(COLLECTION_PAUSE_MILLIS)
        }
        return collectable.get() == null
    }

    /** Closes the class loader's jars; the project's classes go once nothing holds them. Closing twice is harmless. */
    override fun close() {
        loader?.close()
        loader = null
    }

    private fun error(message: String) = Finding(Severity.Error, message, PROJECT_MANIFEST, code = CAPABILITY_CODE)

    private sealed interface Loaded {
        class Capability(val capability: SceneCapability) : Loaded

        class Refused(val why: String) : Loaded
    }

    private companion object {
        const val CAPABILITY_CODE = "capability_class"
        const val COLLECTIONS = 10
        const val COLLECTION_PAUSE_MILLIS = 50L
    }
}

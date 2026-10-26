/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.core.graphics2d.DrawCommand
import com.awakekt.awake.core.localization.LocaleId
import com.awakekt.awake.core.localization.StringTableSource
import com.awakekt.awake.core.localization.Strings
import com.awakekt.awake.core.logging.Log
import com.awakekt.awake.core.logging.LogLevel
import com.awakekt.awake.core.logging.LogSink
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.canvas.CanvasElementBinding.toComponent
import com.awakekt.awake.scene.canvas.CanvasElementBinding.toSceneComponent
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.ui.canvas.CanvasBinding
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasElementKind
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** A game's own state, which a keyed element's argument reads, as its capability would register it. */
private class Bag(var count: Int)

@Serializable
@SerialName("bag")
private data class SceneBag(val count: Int = 0) : SceneComponent

private object BagBinding : SceneComponentBinding<Bag, SceneBag> {
    override val componentClass: KClass<Bag> = Bag::class
    override val schemaClass: KClass<SceneBag> = SceneBag::class
    override val serializer = SceneBag.serializer()

    override fun attachTyped(world: World, entity: Entity, component: SceneBag, context: SceneResolutionContext) {
        world.add(entity, Bag(component.count))
    }

    override fun export(world: World, entity: Entity, component: Bag): SceneBag = SceneBag(component.count)
}

private val Filipino = LocaleId.of("fil")

private val TABLES = StringTableSource.of(
    mapOf(
        LocaleId.English to mapOf(
            "hud.greeting" to "Welcome, {name}!",
            "hud.items" to "{count, plural, one {# item} other {# items}}",
            "menu.start" to "Start",
        ),
        Filipino to mapOf(
            "hud.greeting" to "Maligayang pagdating, {name}!",
            "hud.items" to "{count} na gamit",
            "menu.start" to "Simulan",
        ),
    ),
)

/** A canvas element's `textKey`: its text in the active locale, its arguments, a switch, a missing key, and the saved form. */
class CanvasTextKeyTest {
    private val world = World()
    private val host = ComposeHost()
    private val bag = Bag(count = 1)

    init {
        world.create().also {
            world.add(it, Name("Player"))
            world.add(it, bag)
        }
    }

    private fun element(configure: CanvasElement.() -> Unit): CanvasElement =
        CanvasElement().apply {
            offsetX = 0f
            offsetY = 0f
            width = 400f
            height = 40f
            configure()
        }.also { element -> world.create().also { world.add(it, element) } }

    private fun glyphs(strings: Strings?): List<DrawCommand.Glyph> =
        host.frame(FrameInput(WIDTH, HEIGHT)) { SceneCanvas(world, bindings = listOf(BagBinding), strings = strings) }
            .primitives.filterIsInstance<DrawCommand.Glyph>()

    /** What an element of [kind] with the literal [text] draws, for comparing a keyed one against. */
    private fun literal(text: String, kind: CanvasElementKind = CanvasElementKind.Text): List<DrawCommand.Glyph> =
        CanvasTextKeyTest().run {
            element { this.kind = kind; this.text = text }
            glyphs(strings = null)
        }

    @Test
    fun aKeyedTextShowsItsKeysTextInEachLocale() = runTest {
        element { textKey = "hud.greeting"; textArgs = mapOf("name" to "Ana") }

        val english = glyphs(Strings.load(TABLES, locale = LocaleId.English))
        val filipino = glyphs(Strings.load(TABLES, locale = Filipino))

        assertEquals(literal("Welcome, Ana!"), english)
        assertEquals(literal("Maligayang pagdating, Ana!"), filipino)
    }

    @Test
    fun aLocaleSwitchShowsOnTheNextFrameWithNoReload() = runTest {
        element { kind = CanvasElementKind.Button; textKey = "menu.start" }
        val strings = Strings.load(TABLES, locale = LocaleId.English)

        val before = glyphs(strings)
        strings.setLocale(Filipino)
        val after = glyphs(strings)

        assertEquals(literal("Start", CanvasElementKind.Button), before)
        assertEquals(literal("Simulan", CanvasElementKind.Button), after, "the same element, a frame after the switch")
    }

    @Test
    fun anArgumentBoundToAFieldFollowsWhatTheGameSets() = runTest {
        element {
            textKey = "hud.items"
            textArgs = mapOf("count" to "99")
            bind = CanvasBinding("Player", textArgs = mapOf("count" to "bag.count"))
        }
        val strings = Strings.load(TABLES, locale = LocaleId.English)

        val one = glyphs(strings)
        bag.count = 3
        val three = glyphs(strings)

        assertEquals(literal("1 item"), one, "the field, not the scene's own value, and as a number for the plural")
        assertEquals(literal("3 items"), three)
    }

    @Test
    fun aFieldThatReadsNothingLeavesTheScenesOwnArgument() = runTest {
        element {
            textKey = "hud.greeting"
            textArgs = mapOf("name" to "Ana")
            bind = CanvasBinding("Nobody", textArgs = mapOf("name" to "bag.owner"))
        }

        assertEquals(literal("Welcome, Ana!"), glyphs(Strings.load(TABLES, locale = LocaleId.English)))
    }

    @Test
    fun aMissingKeyShowsTheKeyAndWarnsOnce() = runTest {
        element { textKey = "hud.nowhere" }
        val strings = Strings.load(TABLES, locale = LocaleId.English)
        val warnings = mutableListOf<String>()
        val sink = LogSink { if (it.level == LogLevel.Warn && "hud.nowhere" in it.message) warnings += it.message }
        Log.install(sink)
        try {
            val first = glyphs(strings)
            val second = glyphs(strings)

            assertEquals(literal("hud.nowhere"), first)
            assertEquals(first, second)
            assertEquals(1, warnings.size, "logged once over two frames: $warnings")
        } finally {
            Log.remove(sink)
        }
    }

    @Test
    fun withoutStringsAKeyedElementShowsItsKey() {
        element { textKey = "menu.start" }

        assertEquals(literal("menu.start"), glyphs(strings = null))
    }

    @Test
    fun literalTextIsUnchanged() = runTest {
        element { text = "Start" }

        assertEquals(literal("Start"), glyphs(Strings.load(TABLES, locale = Filipino)), "an element with no key is not looked up")
    }

    @Test
    fun theSavedFormRoundTripsTheKeyAndItsArguments() {
        val saved = SceneCanvasElement(
            kind = CanvasElementKind.Button,
            textKey = "hud.items",
            textArgs = mapOf("count" to "2", "name" to "Ana"),
            bind = CanvasBinding("Player", textArgs = mapOf("count" to "bag.count")),
        )
        val registry = SceneComponentRegistry.scoped(bindings = listOf(CanvasElementBinding, BagBinding))
        val document = SceneDocument(name = "hud", nodes = listOf(SceneNode(name = "Items", components = listOf(saved))))

        val json = SceneLoader.encode(document, registry.sceneJson())
        val decoded = SceneLoader.decode(json, registry.sceneJson())

        assertEquals(saved, saved.toComponent().toSceneComponent(), "through the live element")
        assertEquals(document, decoded, "through the scene file")
        assertTrue("\"textKey\": \"hud.items\"" in json || "\"textKey\":\"hud.items\"" in json, json)
    }

    @Test
    fun aSceneSavedBeforeKeysLoadsAsLiteralText() {
        val registry = SceneComponentRegistry.scoped(bindings = listOf(CanvasElementBinding))
        val old = """
            { "version": 1, "name": "hud", "nodes": [
              { "name": "Title", "components": [ { "component": "canvas_element", "text": "Harbor Town", "bind": { "node": "Player", "text": "{bag.count}" } } ] }
            ] }
        """.trimIndent()

        val element = SceneLoader.decode(old, registry.sceneJson()).nodes.single().components.single() as SceneCanvasElement

        assertEquals("", element.textKey)
        assertEquals(emptyMap(), element.textArgs)
        assertEquals(emptyMap(), element.bind?.textArgs)
        assertEquals("Harbor Town", element.toComponent().text)
    }

    @Test
    fun validationReportsAKeyThatCannotShow() {
        fun issues(element: SceneCanvasElement) = element.validate("n").map { it.message }

        assertEquals(emptyList(), issues(SceneCanvasElement(textKey = "hud.items", textArgs = mapOf("count" to "2"))))
        assertEquals(emptyList(), issues(SceneCanvasElement(kind = CanvasElementKind.Button, textKey = "menu.start")))
        assertEquals(listOf("canvas_element.textKey only sets a Text's or a Button's words, or a Bar's label"), issues(SceneCanvasElement(kind = CanvasElementKind.Panel, textKey = "menu.start")))
        assertEquals(listOf("canvas_element.textKey and text both set the words; keep one"), issues(SceneCanvasElement(text = "Start", textKey = "menu.start")))
        assertEquals(
            listOf("canvas_element.textKey and bind.text both set the words; keep one"),
            issues(SceneCanvasElement(textKey = "menu.start", bind = CanvasBinding("Player", text = "{bag.count}"))),
        )
        assertEquals(listOf("canvas_element.textArgs fill in a textKey's, placeholder's or tooltip's text; set one"), issues(SceneCanvasElement(textArgs = mapOf("count" to "2"))))
        assertEquals(
            listOf("canvas_element.bind.textArgs \"count\" must be component.field, not \"count\""),
            issues(SceneCanvasElement(textKey = "hud.items", bind = CanvasBinding("Player", textArgs = mapOf("count" to "count")))),
        )
        assertNotEquals(emptyList(), issues(SceneCanvasElement(textKey = "  ")))
    }

    private companion object {
        const val WIDTH = 800
        const val HEIGHT = 200
    }
}

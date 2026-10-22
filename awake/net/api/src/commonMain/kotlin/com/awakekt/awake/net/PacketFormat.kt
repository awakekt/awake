/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("TooManyFunctions", "ReturnCount", "ComplexCondition")

package com.awakekt.awake.net

import kotlinx.serialization.BinaryFormat
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialInfo
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import kotlin.math.roundToInt
import kotlin.reflect.KClass

/**
 * Bounds the UTF-8 byte length of a serialized [String] property.
 *
 * During decoding, the declared length prefix is validated against [max] and [PacketReader.remaining]
 * before allocating the backing byte array, preventing hostile length-prefix allocation attacks.
 *
 * @property max Maximum allowed UTF-8 byte length.
 * @property nonEmpty Whether an empty string (`""`) is rejected during decoding.
 * @property nonBlank Whether a blank string is rejected during decoding.
 */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
annotation class PacketMaxBytes(
    val max: Int,
    val nonEmpty: Boolean = false,
    val nonBlank: Boolean = false,
)

/**
 * Encodes a nullable `String?` property as an empty string (`""`) on the wire instead of writing a
 * separate `0`/`1` presence byte, and decodes `""` back to `null`.
 */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
annotation class PacketEmptyNull

/**
 * Bounds the element count of a serialized collection (`List`, `Set`, or `Map`) property.
 *
 * During decoding, the element count is checked against `min..max` before allocating the collection.
 * When [fixed] is `true`, [min] and [max] must be equal and no count varint is written on the wire.
 *
 * @property max Maximum allowed element count.
 * @property min Minimum allowed element count (defaults to `0`).
 * @property fixed Whether the collection has a fixed wire size of [max] with no length prefix.
 */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
annotation class PacketCollectionSize(
    val max: Int,
    val min: Int = 0,
    val fixed: Boolean = false,
)

/**
 * Constrains a serialized [Int] property to `min..max` on decode and optionally clamps or masks it on encode.
 *
 * @property min Minimum accepted value on decode (inclusive).
 * @property max Maximum accepted value on decode (inclusive).
 * @property clampOnEncode Whether to coerce the value into `min..max` when encoding.
 * @property byteWire Whether to encode the integer as a single byte instead of a varint.
 * @property bitMask Optional bitmask applied with bitwise `and` on encode (`0` disables masking).
 */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
annotation class PacketIntRange(
    val min: Int = 0,
    val max: Int = Int.MAX_VALUE,
    val clampOnEncode: Boolean = true,
    val byteWire: Boolean = false,
    val bitMask: Int = 0,
)

/**
 * Constrains a serialized [Long] property to `min..max` on decode and optionally clamps it on encode.
 *
 * @property min Minimum accepted value on decode (inclusive).
 * @property max Maximum accepted value on decode (inclusive).
 * @property clampOnEncode Whether to coerce the value into `min..max` when encoding.
 */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
annotation class PacketLongRange(
    val min: Long = 0L,
    val max: Long = Long.MAX_VALUE,
    val clampOnEncode: Boolean = true,
)

/**
 * Quantizes a [Float] property into millimeter units (`(value * 1000f).roundToInt()`) encoded as a
 * ZigZag varint (or unsigned varint when [unsigned] is `true`).
 *
 * @property unsigned Whether to clamp negative values to `0` and use an unsigned varint instead of ZigZag.
 */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
annotation class PacketMillis(val unsigned: Boolean = false)

/**
 * Encodes a nullable [NetId] (`NetId?`) as `0L` when `null` without a separate presence byte, and
 * decodes `0L` back to `null` (rejecting negative raw values).
 */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
annotation class PacketZeroNullNetId

/**
 * Compact, tagless binary [BinaryFormat] backed by [PacketWriter] and [PacketReader].
 *
 * Unlike reflection- or exception-driven wire formats, [decodeFromReader] validates string byte lengths
 * ([PacketMaxBytes]), collection sizes ([PacketCollectionSize]), and numeric bounds ([PacketIntRange],
 * [PacketLongRange]) before allocating, and signals malformed input by returning `null` rather than
 * throwing an exception.
 *
 * @property serializersModule Contextual and polymorphic serializers module.
 * @property defaultMaxStringBytes Fallback UTF-8 byte limit for strings without [PacketMaxBytes].
 * @property defaultMaxCollectionSize Fallback element limit for collections without [PacketCollectionSize].
 */
@OptIn(ExperimentalSerializationApi::class)
class PacketFormat(
    override val serializersModule: SerializersModule = EmptySerializersModule(),
    val defaultMaxStringBytes: Int = DEFAULT_MAX_STRING_BYTES,
    val defaultMaxCollectionSize: Int = DEFAULT_MAX_COLLECTION_SIZE,
) : BinaryFormat {

    /**
     * Encodes [value] into [writer] using [serializer].
     *
     * @param T Type of the value being encoded.
     * @param writer Target packet writer cursor.
     * @param serializer Serialization strategy for [T].
     * @param value Value to encode.
     */
    fun <T> encodeToWriter(writer: PacketWriter, serializer: SerializationStrategy<T>, value: T) {
        val encoder = PacketFieldEncoder(this, writer)
        encoder.encodeSerializableValue(serializer, value)
    }

    /**
     * Decodes a value of type [T] from [reader] using [deserializer], or returns `null` if the packet
     * is truncated, corrupted, or violates any field constraint.
     *
     * @param T Type of the value being decoded.
     * @param reader Source packet reader cursor.
     * @param deserializer Deserialization strategy for [T].
     * @return Decoded value, or `null` on malformed input.
     */
    fun <T> decodeFromReader(reader: PacketReader, deserializer: DeserializationStrategy<T>): T? {
        val state = DecodeState(this, reader)
        val decoder = PacketFieldDecoder(state)
        val value = decoder.decodeSerializableValue(deserializer)
        return value.takeIf { reader.ok && !state.failed }
    }

    /**
     * Decodes a complete packet [bytes] using [deserializer], requiring all bytes to be consumed.
     *
     * @param T Type of the value being decoded.
     * @param deserializer Deserialization strategy for [T].
     * @param bytes Raw packet bytes.
     * @return Decoded value, or `null` if malformed or trailing bytes remain.
     */
    fun <T> decodeOrNull(deserializer: DeserializationStrategy<T>, bytes: ByteArray): T? {
        val reader = PacketReader()
        reader.reset(bytes)
        val value = decodeFromReader(reader, deserializer) ?: return null
        return value.takeIf { reader.remaining == 0 }
    }

    override fun <T> encodeToByteArray(serializer: SerializationStrategy<T>, value: T): ByteArray {
        val writer = PacketWriter()
        encodeToWriter(writer, serializer, value)
        return writer.toPacket()
    }

    override fun <T> decodeFromByteArray(deserializer: DeserializationStrategy<T>, bytes: ByteArray): T =
        decodeOrNull(deserializer, bytes) ?: throw SerializationException("Malformed packet for ${deserializer.descriptor.serialName}")

    /** Default configuration and constants for [PacketFormat]. */
    companion object {
        private const val DEFAULT_MAX_STRING_BYTES = 1024
        private const val DEFAULT_MAX_COLLECTION_SIZE = 256
        internal const val MILLI_SCALE = 1000f

        /** Default [PacketFormat] instance with standard limits. */
        val Default: PacketFormat = PacketFormat()
    }
}

/**
 * Bidirectional opcode table mapping integer wire opcodes to `@Serializable` packet types under [Base].
 *
 * Eliminates repetitive `when (message)` / `when (opcode)` boilerplate by dispatching registered packet
 * classes through [PacketFormat] while allowing custom per-message invariants via `validator`.
 *
 * @param Base Sealed interface or base type shared by all messages in the protocol direction.
 * @property format Underlying [PacketFormat] used to read and write message fields.
 */
class PacketOpcodeRegistry<Base : Any>(val format: PacketFormat = PacketFormat.Default) {
    private class Entry<Base : Any, M : Base>(
        val opcode: Int,
        val serializer: KSerializer<M>,
        val validator: (M) -> Boolean,
    ) {
        fun encodePayload(format: PacketFormat, writer: PacketWriter, message: Base) {
            @Suppress("UNCHECKED_CAST")
            val typed = message as M
            require(validator(typed)) { "Invalid outbound packet ${serializer.descriptor.serialName}" }
            format.encodeToWriter(writer, serializer, typed)
        }

        fun decodePayload(format: PacketFormat, reader: PacketReader): Base? {
            val decoded = format.decodeFromReader(reader, serializer) ?: return null
            return decoded.takeIf(validator)
        }
    }

    private val byOpcode = HashMap<Int, Entry<Base, out Base>>()
    private val byClass = HashMap<KClass<out Base>, Entry<Base, out Base>>()

    /**
     * Registers [opcode] for message [kClass] with [serializer] and an optional post-decode/pre-encode [validator].
     *
     * @param M Specific message subtype being registered.
     * @param opcode Wire varint opcode identifying this message type.
     * @param kClass Runtime class of the message subtype.
     * @param serializer Compiler-generated [KSerializer] for [M].
     * @param validator Additional domain invariant check that must hold on encode and decode.
     */
    fun <M : Base> register(
        opcode: Int,
        kClass: KClass<M>,
        serializer: KSerializer<M>,
        validator: (M) -> Boolean = { true },
    ) {
        require(opcode !in byOpcode) { "Duplicate packet opcode $opcode" }
        require(kClass !in byClass) { "Duplicate packet class $kClass" }
        val entry = Entry<Base, M>(opcode, serializer, validator)
        byOpcode[opcode] = entry
        byClass[kClass] = entry
    }

    /**
     * Registers [opcode] for reified `@Serializable` message type [M].
     *
     * @param M Specific message subtype being registered.
     * @param opcode Wire varint opcode identifying [M].
     * @param validator Additional domain invariant check that must hold on encode and decode.
     */
    inline fun <reified M : Base> register(
        opcode: Int,
        noinline validator: (M) -> Boolean = { true },
    ) {
        register(opcode, M::class, serializer<M>(), validator)
    }

    /**
     * Writes the registered opcode varint and serialized fields of [message] into [writer].
     *
     * @param writer Target packet writer.
     * @param message Message instance to encode.
     * @return `true` if [message]'s class is registered in this table and was encoded, `false` otherwise.
     */
    fun encode(writer: PacketWriter, message: Base): Boolean {
        val entry = byClass[message::class] ?: return false
        writer.writeVarInt(entry.opcode)
        entry.encodePayload(format, writer, message)
        return true
    }

    /**
     * Decodes the payload for [opcode] from [reader], returning `null` if [opcode] is unknown or the packet is invalid.
     *
     * @param opcode Wire opcode already read from [reader].
     * @param reader Source packet reader positioned at the message payload.
     * @return Decoded and validated message, or `null`.
     */
    fun decode(opcode: Int, reader: PacketReader): Base? =
        byOpcode[opcode]?.decodePayload(format, reader)
}

@OptIn(ExperimentalSerializationApi::class)
private class PacketFieldEncoder(
    private val format: PacketFormat,
    private val writer: PacketWriter,
) : AbstractEncoder() {
    override val serializersModule: SerializersModule get() = format.serializersModule

    private var activeDescriptor: SerialDescriptor? = null
    private var activeIndex: Int = -1

    override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
        val kindName = descriptor.kind.toString()
        if (!kindName.endsWith("LIST") && !kindName.endsWith("MAP")) {
            activeDescriptor = descriptor
            activeIndex = index
        }
        return true
    }

    override fun beginCollection(descriptor: SerialDescriptor, collectionSize: Int): CompositeEncoder {
        val bounds = currentAnnotation<PacketCollectionSize>()
        if (bounds != null) {
            require(collectionSize in bounds.min..bounds.max) {
                "Collection size $collectionSize outside ${bounds.min}..${bounds.max} for ${activeDescriptor?.serialName}"
            }
            if (!bounds.fixed) writer.writeVarInt(collectionSize)
        } else {
            require(collectionSize <= format.defaultMaxCollectionSize) {
                "Collection size $collectionSize exceeds ${format.defaultMaxCollectionSize}"
            }
            writer.writeVarInt(collectionSize)
        }
        return this
    }

    override fun encodeNull() {
        writer.writeByte(0)
    }

    override fun encodeNotNullMark() {
        writer.writeByte(1)
    }

    override fun <T : Any> encodeNullableSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        serializer: SerializationStrategy<T>,
        value: T?,
    ) {
        activeDescriptor = descriptor
        activeIndex = index
        if (currentAnnotation<PacketZeroNullNetId>() != null) {
            writer.writeVarLong((value as? NetId)?.value ?: 0L)
            return
        }
        if (currentAnnotation<PacketEmptyNull>() != null) {
            writeBoundedString((value as? String).orEmpty(), currentAnnotation<PacketMaxBytes>())
            return
        }
        super.encodeNullableSerializableElement(descriptor, index, serializer, value)
    }

    override fun encodeBoolean(value: Boolean) {
        writer.writeByte(if (value) 1 else 0)
    }

    override fun encodeByte(value: Byte) {
        writer.writeByte(value)
    }

    override fun encodeShort(value: Short) {
        writer.writeShort(value)
    }

    override fun encodeInt(value: Int) {
        val range = currentAnnotation<PacketIntRange>()
        var adjusted = value
        if (range != null) {
            if (range.bitMask != 0) adjusted = adjusted and range.bitMask
            if (range.clampOnEncode) adjusted = adjusted.coerceIn(range.min, range.max)
            if (range.byteWire) {
                writer.writeByte(adjusted.toByte())
                return
            }
        }
        writer.writeVarInt(adjusted)
    }

    override fun encodeLong(value: Long) {
        val range = currentAnnotation<PacketLongRange>()
        val adjusted = if (range != null && range.clampOnEncode) value.coerceIn(range.min, range.max) else value
        writer.writeVarLong(adjusted)
    }

    override fun encodeFloat(value: Float) {
        val millis = currentAnnotation<PacketMillis>()
        if (millis != null) {
            val scaled = ((if (millis.unsigned) value.coerceAtLeast(0f) else value) * PacketFormat.MILLI_SCALE).roundToInt()
            if (millis.unsigned) writer.writeVarInt(scaled) else writer.writeZigZagInt(scaled)
        } else {
            writer.writeVarInt(value.toRawBits())
        }
    }

    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) {
        writer.writeByte(index.toByte())
    }

    override fun encodeString(value: String) {
        writeBoundedString(value, currentAnnotation<PacketMaxBytes>())
    }

    private fun writeBoundedString(value: String, bounds: PacketMaxBytes?) {
        val maxBytes = bounds?.max ?: format.defaultMaxStringBytes
        val bytes = value.encodeToByteArray()
        require(bytes.size <= maxBytes) { "Text of ${bytes.size} bytes is over the $maxBytes allowed" }
        writer.writeVarInt(bytes.size)
        bytes.forEach { writer.writeByte(it) }
    }

    private inline fun <reified A : Annotation> currentAnnotation(): A? {
        val desc = activeDescriptor ?: return null
        val idx = activeIndex
        if (idx !in 0 until desc.elementsCount) return null
        return desc.getElementAnnotations(idx).filterIsInstance<A>().firstOrNull()
    }
}

private class DecodeState(
    val format: PacketFormat,
    val reader: PacketReader,
    var failed: Boolean = false,
)

@OptIn(ExperimentalSerializationApi::class)
private class PacketFieldDecoder(
    private val state: DecodeState,
    private val parentDescriptor: SerialDescriptor? = null,
    private val parentIndex: Int = -1,
    private val collectionElementsCount: Int = -1,
) : AbstractDecoder() {
    private val format: PacketFormat get() = state.format
    private val reader: PacketReader get() = state.reader
    override val serializersModule: SerializersModule get() = format.serializersModule

    private var elementCursor: Int = 0
    private var activeDescriptor: SerialDescriptor? = parentDescriptor
    private var activeIndex: Int = parentIndex
    private var hasPendingLong: Boolean = false
    private var pendingLong: Long = 0L
    private var hasPendingString: Boolean = false
    private var pendingString: String = ""

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        val limit = if (collectionElementsCount >= 0) collectionElementsCount else descriptor.elementsCount
        if (elementCursor >= limit) return CompositeDecoder.DECODE_DONE
        if (collectionElementsCount < 0) {
            activeDescriptor = descriptor
            activeIndex = elementCursor
        }
        return elementCursor++
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        val kindName = descriptor.kind.toString()
        val isCollection = kindName.endsWith("LIST") || kindName.endsWith("MAP")
        if (isCollection) {
            val count = readCollectionCount()
            val elementSlots = if (kindName.endsWith("MAP")) count * 2 else count
            return PacketFieldDecoder(state, activeDescriptor, activeIndex, elementSlots)
        }
        return PacketFieldDecoder(state, activeDescriptor, activeIndex)
    }

    private fun readCollectionCount(): Int {
        if (state.failed || !reader.ok) return 0
        val bounds = currentAnnotation<PacketCollectionSize>() ?: parentAnnotation<PacketCollectionSize>()
        val min = bounds?.min ?: 0
        val max = bounds?.max ?: format.defaultMaxCollectionSize
        val count = if (bounds?.fixed == true) max else reader.readVarInt()
        if (!reader.ok || count !in min..max) {
            markFailed()
            return 0
        }
        return count
    }

    override fun decodeNotNullMark(): Boolean {
        if (currentAnnotation<PacketZeroNullNetId>() != null) {
            val raw = reader.readVarLong()
            if (!reader.ok || raw < 0L) {
                markFailed()
                return false
            }
            if (raw == 0L) return false
            pendingLong = raw
            hasPendingLong = true
            return true
        }
        if (currentAnnotation<PacketEmptyNull>() != null) {
            val str = readBoundedString(currentAnnotation<PacketMaxBytes>())
            if (state.failed || str.isEmpty()) return false
            pendingString = str
            hasPendingString = true
            return true
        }
        return decodeFlag()
    }

    override fun decodeNull(): Nothing? = null

    override fun decodeBoolean(): Boolean = decodeFlag()

    override fun decodeByte(): Byte = reader.readByte()

    override fun decodeShort(): Short = reader.readShort()

    override fun decodeInt(): Int {
        val range = currentAnnotation<PacketIntRange>()
        val value = if (range?.byteWire == true) reader.readByte().toInt() else reader.readVarInt()
        if (!reader.ok || (range != null && value !in range.min..range.max)) {
            markFailed()
            return 0
        }
        return value
    }

    override fun decodeLong(): Long {
        if (hasPendingLong) {
            hasPendingLong = false
            return pendingLong
        }
        val range = currentAnnotation<PacketLongRange>()
        val value = reader.readVarLong()
        if (!reader.ok || (range != null && value !in range.min..range.max)) {
            markFailed()
            return 0L
        }
        return value
    }

    override fun decodeFloat(): Float {
        val millis = currentAnnotation<PacketMillis>()
        if (millis != null) {
            val raw = if (millis.unsigned) reader.readVarInt() else reader.readZigZagInt()
            if (!reader.ok || (millis.unsigned && raw < 0)) {
                markFailed()
                return 0f
            }
            return raw / PacketFormat.MILLI_SCALE
        }
        return Float.fromBits(reader.readVarInt())
    }

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int {
        val ordinal = reader.readByte().toInt()
        if (!reader.ok || ordinal !in 0 until enumDescriptor.elementsCount) {
            markFailed()
            return 0
        }
        return ordinal
    }

    override fun decodeString(): String {
        if (hasPendingString) {
            hasPendingString = false
            return pendingString
        }
        return readBoundedString(currentAnnotation<PacketMaxBytes>())
    }

    private fun readBoundedString(bounds: PacketMaxBytes?): String {
        val maxBytes = bounds?.max ?: format.defaultMaxStringBytes
        val length = reader.readVarInt()
        if (!reader.ok || length !in 0..maxBytes || length > reader.remaining) {
            markFailed()
            return ""
        }
        val text = ByteArray(length) { reader.readByte() }.decodeToString()
        if ((bounds?.nonEmpty == true && text.isEmpty()) || (bounds?.nonBlank == true && text.isBlank())) {
            markFailed()
            return ""
        }
        return text
    }

    private fun decodeFlag(): Boolean = when (reader.readByte().toInt()) {
        0 -> {
            if (!reader.ok) markFailed()
            false
        }
        1 -> true
        else -> {
            markFailed()
            false
        }
    }

    private fun markFailed() {
        state.failed = true
    }

    private inline fun <reified A : Annotation> currentAnnotation(): A? {
        val desc = activeDescriptor ?: return null
        val idx = activeIndex
        if (idx !in 0 until desc.elementsCount) return null
        return desc.getElementAnnotations(idx).filterIsInstance<A>().firstOrNull()
    }

    private inline fun <reified A : Annotation> parentAnnotation(): A? {
        val desc = parentDescriptor ?: return null
        val idx = parentIndex
        if (idx !in 0 until desc.elementsCount) return null
        return desc.getElementAnnotations(idx).filterIsInstance<A>().firstOrNull()
    }
}

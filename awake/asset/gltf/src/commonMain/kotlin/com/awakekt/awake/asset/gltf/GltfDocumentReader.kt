/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.gltf

import com.awakekt.awake.core.animation.AnimationChannel
import com.awakekt.awake.core.animation.AnimationClip
import com.awakekt.awake.core.animation.AnimationProperty
import com.awakekt.awake.core.animation.AnimationSampler
import com.awakekt.awake.core.animation.Skin
import com.awakekt.awake.core.math.Mat4
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private const val BASE64_DATA_URI_MARKER = ";base64,"
private const val BASE64_GROUP_CHARS = 4
private const val BASE64_GROUP_BYTES = 3
private const val MAT4_COMPONENTS = 16
private const val CUBIC_SPLINE_VALUES_PER_KEYFRAME = 3

/**
 * Reads [document]'s primitives, materials' images, skins and animations out of its decoded [buffers],
 * failing with [GltfFormatException] on anything that does not fit. Each image is read once however many
 * primitives use it.
 */
internal class GltfDocumentReader(
    private val document: GltfDocument,
    buffers: List<ByteArray>,
    private val externalResources: Map<String, ByteArray>,
    private val context: GltfReadContext,
) {
    private val accessors = GltfAccessorReader(document, buffers, context)
    private val images = HashMap<Int, ByteArray?>()

    /** [primitive], found at [location], with its attributes, indices and material. */
    fun readPrimitive(primitive: GltfPrimitive, location: String): GltfMesh {
        val attributes = primitive.attributes
        val positionAccessor = attributes.position ?: malformed("$location.attributes", "has no POSITION attribute")
        val positions = accessors.floats(positionAccessor, 3, "$location.attributes.POSITION")
        val vertexCount = positions.size / 3

        fun attribute(name: String, index: Int?, components: Int): FloatArray? = index?.let {
            requireVertexCount(it, vertexCount, "$location.attributes.$name")
            accessors.floats(it, components, "$location.attributes.$name")
        }

        val indices = primitive.indices?.let { accessors.indices(it, vertexCount, "$location.indices") }
            ?: IntArray(context.arrayLength(location, vertexCount, 1)) { it }
        val joints = attributes.joints0?.let {
            requireVertexCount(it, vertexCount, "$location.attributes.JOINTS_0")
            accessors.joints(it, "$location.attributes.JOINTS_0")
        }
        val material = primitive.material?.let { document.materials.getOrNull(it) }
        val pbr = material?.pbrMetallicRoughness
        val materialLocation = "materials[${primitive.material}]"
        return GltfMesh(
            positions = positions,
            normals = attribute("NORMAL", attributes.normal, 3),
            colors = attribute("COLOR_0", attributes.color0, 3),
            uvs = attribute("TEXCOORD_0", attributes.texCoord0, 2),
            indices = indices,
            jointIndices = joints,
            jointWeights = attribute("WEIGHTS_0", attributes.weights0, JOINTS_PER_VERTEX),
            baseColorImageBytes = materialImage(pbr?.baseColorTexture),
            metallicRoughnessImageBytes = materialImage(pbr?.metallicRoughnessTexture),
            normalImageBytes = materialImage(material?.normalTexture),
            occlusionImageBytes = materialImage(material?.occlusionTexture),
            emissiveImageBytes = materialImage(material?.emissiveTexture),
            baseColorFactor = factor(pbr?.baseColorFactor, RGBA, "$materialLocation.pbrMetallicRoughness.baseColorFactor")
                ?: floatArrayOf(1f, 1f, 1f, 1f),
            metallicFactor = pbr?.metallicFactor ?: 1f,
            roughnessFactor = pbr?.roughnessFactor ?: 1f,
            emissiveFactor = factor(material?.emissiveFactor, RGB, "$materialLocation.emissiveFactor") ?: floatArrayOf(0f, 0f, 0f),
            alphaMode = material?.alphaMode ?: GltfAlphaMode.OPAQUE,
        )
    }

    /** Skin [skinIndex] with an inverse bind matrix per joint (identity when the skin has none). */
    fun readSkin(skinIndex: Int): Skin {
        val location = "skins[$skinIndex]"
        val skin = document.skins[skinIndex]
        skin.joints.forEachIndexed { slot, node ->
            if (node !in document.nodes.indices) {
                malformed("$location.joints[$slot]", "is node $node, but the document has ${document.nodes.size} nodes")
            }
        }
        skin.skeleton?.let { node ->
            if (node !in document.nodes.indices) {
                malformed("$location.skeleton", "is node $node, but the document has ${document.nodes.size} nodes")
            }
        }
        val inverseBind = skin.inverseBindMatrices?.let {
            accessors.floats(it, MAT4_COMPONENTS, "$location.inverseBindMatrices")
        }
        if (inverseBind != null && inverseBind.size / MAT4_COMPONENTS < skin.joints.size) {
            malformed(
                "$location.inverseBindMatrices",
                "holds ${inverseBind.size / MAT4_COMPONENTS} matrices for ${skin.joints.size} joints",
            )
        }
        val matrices = skin.joints.indices.map { joint ->
            Mat4().apply {
                // No inverseBindMatrices accessor: glTF's default is identity per joint.
                inverseBind?.copyInto(data, 0, joint * MAT4_COMPONENTS, (joint + 1) * MAT4_COMPONENTS)
            }
        }
        return Skin(skin.joints, matrices)
    }

    /** Animation [animationIndex]'s translation, rotation and scale channels; morph `weights` are skipped. */
    fun readAnimation(animationIndex: Int): AnimationClip {
        val location = "animations[$animationIndex]"
        val animation = document.animations[animationIndex]
        val channels = animation.channels.mapIndexedNotNull { channelIndex, channel ->
            val channelLocation = "$location.channels[$channelIndex]"
            val targetNode = channel.target.node ?: return@mapIndexedNotNull null
            if (targetNode !in document.nodes.indices) {
                malformed("$channelLocation.target.node", "is node $targetNode, but the document has ${document.nodes.size} nodes")
            }
            val property = when (channel.target.path) {
                "translation" -> AnimationProperty.Translation
                "scale" -> AnimationProperty.Scale
                "rotation" -> AnimationProperty.Rotation
                else -> return@mapIndexedNotNull null // "weights" (morph targets) -- not supported.
            }
            val sampler = animation.samplers.getOrNull(channel.sampler)
                ?: malformed("$channelLocation.sampler", "is ${channel.sampler}, but the animation has ${animation.samplers.size} samplers")
            val components = if (property == AnimationProperty.Rotation) 4 else 3
            AnimationChannel(targetNode, property, readSampler(sampler, components, "$location.samplers[${channel.sampler}]"))
        }
        return AnimationClip(name = animation.name, channels = channels)
    }

    private fun readSampler(sampler: GltfAnimationSampler, components: Int, location: String): AnimationSampler {
        val times = accessors.floats(sampler.input, 1, "$location.input")
        for (key in times.indices) {
            val time = times[key]
            if (!time.isFinite() || (key > 0 && time < times[key - 1])) {
                malformed("$location.input", "keyframe $key is at $time; keyframe times must be finite and in order")
            }
        }
        val values = accessors.floats(sampler.output, components, "$location.output")
        val valuesPerKeyframe = if (sampler.interpolation == "CUBICSPLINE") CUBIC_SPLINE_VALUES_PER_KEYFRAME else 1
        if (values.size / components != times.size * valuesPerKeyframe) {
            malformed(
                "$location.output",
                "holds ${values.size / components} values for ${times.size} keyframes of ${sampler.interpolation}",
            )
        }
        return AnimationSampler(times, values, components)
    }

    private fun requireVertexCount(accessor: Int, vertexCount: Int, referrer: String) {
        val count = accessors.count(accessor, referrer)
        if (count != vertexCount) {
            malformed(referrer, "accessor $accessor holds $count elements, but POSITION holds $vertexCount")
        }
    }

    /** A material texture's still-encoded image bytes, or null when there is no image to read. */
    private fun materialImage(texture: GltfTextureRef?): ByteArray? {
        val imageIndex = texture?.let { document.textures.getOrNull(it.index) }?.source
        if (imageIndex == null || imageIndex !in document.images.indices) return null
        return images.getOrPut(imageIndex) { readImage(imageIndex) }
    }

    private fun readImage(index: Int): ByteArray? {
        val image = document.images[index]
        val uri = image.uri
        return when {
            uri == null -> image.bufferView?.let { accessors.bufferViewBytes(it, "images[$index].bufferView") }
            uri.startsWith("data:") -> decodeDataUri(uri, "images[$index].uri", context)
            else -> externalResources[uri]
        }
    }

    private companion object {
        const val RGB = 3
        const val RGBA = 4
        const val JOINTS_PER_VERTEX = 4
    }
}

private fun factor(values: List<Float>?, size: Int, location: String): FloatArray? = values?.let {
    if (it.size != size) malformed(location, "has ${it.size} values, not $size")
    it.toFloatArray()
}

/** A `data:...;base64,` URI's bytes, charged to [context] before they are decoded. */
@OptIn(ExperimentalEncodingApi::class)
internal fun decodeDataUri(uri: String, location: String, context: GltfReadContext): ByteArray {
    val marker = uri.indexOf(BASE64_DATA_URI_MARKER)
    if (marker < 0) malformed(location, "is a data URI without ';base64,'; only base64 data URIs are read")
    val payloadStart = marker + BASE64_DATA_URI_MARKER.length
    val groups = (uri.length - payloadStart + BASE64_GROUP_CHARS - 1) / BASE64_GROUP_CHARS
    context.charge(location, groups.toLong() * BASE64_GROUP_BYTES)
    return try {
        Base64.decode(uri, payloadStart, uri.length)
    } catch (invalid: IllegalArgumentException) {
        throw GltfFormatException(location, "is not valid base64: ${invalid.message}", invalid)
    }
}

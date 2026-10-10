/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.gltf

import com.awakekt.awake.asset.gltf.GltfMesh
import com.awakekt.awake.asset.gltf.GltfParser
import com.awakekt.awake.asset.gltf.LoadedPrimitive
import com.awakekt.awake.asset.gltf.LoadedScene
import com.awakekt.awake.asset.gltf.LoadedSkinnedScene
import com.awakekt.awake.asset.gltf.bindRigidNode
import com.awakekt.awake.asset.gltf.firstSkinnedAsset
import com.awakekt.awake.asset.gltf.restTransform
import com.awakekt.awake.asset.shaderpack.TexturedUniformLayout
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.host.readResourceBytes
import com.awakekt.awake.core.image.createBitmap
import com.awakekt.awake.core.image.toRgba8Bytes
import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.logging.Logger
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.pipeline.AlphaMode
import com.awakekt.awake.render.renderer.SkinnedUniformLayout
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.rendering.mesh.PbrMaterial
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.scene.runtime.SceneAssetResolver

/**
 * Resolves `.gltf` and `.glb` model paths in scenes to meshes and materials.
 *
 * Resolves ANY `.gltf` or `.glb` path dynamically on demand. Zero hardcoded model or character
 * identifiers. Caches loaded scenes so that entities referencing them can have their skeletons,
 * poses, and animation players bound automatically.
 *
 * A skinned `.gltf` draws as parts, one per primitive: `gltf-primitive:<path>#<i>` is its i-th part
 * and `gltf-material:<path>#<i>` that part's base colour texture. Parts are numbered so a number a
 * saved scene holds keeps its meaning: first each node with both a mesh and a skin, by its mesh's
 * first primitive, in file order; then those meshes' other primitives; then the primitives of every
 * mesh node with no skin. Such a rigid node under a joint of the model's first skin is bound to that
 * joint and follows it; one under no joint is drawn in place as static geometry. A textured skinned
 * part is drawn by a `PositionNormalColorUvSkin` pipeline, so the host's render plan must declare
 * one; skinned parts cast no shadow. The model's own path still names its first skinned node's first
 * primitive, untextured.
 */
@Suppress("TooManyFunctions") // One resolver: preload, query and resolve are one lifecycle.
class GltfAssetResolver(
    private val bundledResourceReader: suspend (String) -> ByteArray = { readResourceBytes(it) },
    private val meshBoundsRegistrar: (String, MeshGeometry) -> Unit = { _, _ -> },
) : SceneAssetResolver {
    private val loadedScenes = mutableMapOf<String, LoadedSkinnedScene>()
    private val loadedStaticMeshes = mutableMapOf<String, MeshGeometry>()
    private val loadedMaterials = mutableMapOf<String, LoadedGltfMaterial>()
    private val skinnedPartTextures = mutableMapOf<String, TextureAsset>()
    private val loadedMaterialSlots = mutableMapOf<String, List<GltfMaterialSlot>>()
    private val loadedParts = mutableMapOf<String, List<GltfPart>>()
    private var assetSource: AssetSource = AssetSource { path ->
        runCatching { bundledResourceReader(path.value) }
    }

    /**
     * Retrieves a previously loaded skinned scene by its path.
     *
     * @param path The glTF or GLB asset path.
     * @return The cached [LoadedSkinnedScene], or null if the path has not been preloaded.
     */
    fun getLoadedScene(path: String): LoadedSkinnedScene? = loadedScenes[path]

    /**
     * Supplies project-file access for JSON glTF sidecars.
     *
     * @param source The asset source to read project assets and sidecars from.
     */
    fun setAssetSource(source: AssetSource) {
        assetSource = source
    }

    internal suspend fun readAsset(path: AssetPath): Result<ByteArray> = assetSource.read(path)

    /**
     * Preloads a glTF or GLB asset and its external dependencies using the configured asset source.
     *
     * @param path The glTF or GLB asset path to load.
     */
    suspend fun preload(path: String) {
        val bytes = assetSource.read(AssetPath(path)).getOrThrow()
        preload(path, bytes)
    }

    /**
     * Preloads caller-provided model bytes and resolves JSON glTF sidecars through [assetSource].
     * Keeping this suspendable prevents an imported `.gltf` from silently losing its external
     * `.bin` or image files just because the model bytes came from a picker.
     *
     * @param path The asset path key for caching the model.
     * @param bytes Raw model file content bytes.
     */
    suspend fun preload(path: String, bytes: ByteArray) {
        val externalResources = if (bytes.isGlb()) {
            emptyMap()
        } else {
            GltfParser.loadExternalResources(
                bytes.decodeToString(),
                AssetPath(path),
                assetSource,
            ).getOrThrow()
        }
        if (path !in loadedScenes || path !in loadedStaticMeshes) {
            preload(path, bytes, externalResources)
        }
        if (path !in loadedMaterialSlots) {
            // A model whose textures fail to load still draws, untextured, with lit-shadow.
            runCatching { preloadMaterials(path, bytes) }
                .onFailure { log.warn { "Drawing '$path' untextured: its materials failed to load (${it.message})" } }
        }
    }

    /** Registers model bytes after external resources have been fetched by the suspendable API. */
    private fun preload(path: String, bytes: ByteArray, externalResources: Map<String, ByteArray>) {
        if (bytes.isGlb()) {
            if (path !in loadedStaticMeshes) {
                loadedStaticMeshes[path] = GltfParser.parseScene(bytes, externalResources).toStaticGeometry()
            }
        } else {
            val json = bytes.decodeToString()
            // Keep the skeletal scene when the file contains skin data; static geometry is also
            // retained as a fallback for a model whose first scene has no usable skin.
            if (path !in loadedScenes) {
                runCatching { loadedScenes[path] = GltfParser.parseSkinned(json, externalResources) }
            }
            if (path !in loadedStaticMeshes) {
                loadedStaticMeshes[path] = runCatching {
                    GltfParser.parseScene(json.toEmbeddedGlb(), externalResources).toStaticGeometry()
                }.getOrElse {
                    // A valid document may omit a scene graph. Preserve the parser's first-primitive
                    // fallback for that narrow shape, while still supporting all scene primitives
                    // when the document provides nodes/scenes.
                    GltfParser.parse(json, externalResources).toStaticGeometry()
                }
            }
        }
    }

    /**
     * Forgets everything parsed from the model file at [path] -- its geometry, skinned scene,
     * materials and part textures -- so the next [preload] reads the file again. An editor calls
     * this when the file changed on disk. Meshes and materials already created from it are not
     * touched; they stay until their holders release them.
     */
    fun forget(path: String) {
        loadedScenes.remove(path)
        loadedParts.remove(path)
        loadedMaterialSlots.remove(path)
        loadedStaticMeshes.keys.removeAll { it == path || it.startsWith("$PRIMITIVE_MESH_PREFIX$path#") }
        loadedMaterials.keys.removeAll { it == path || it.startsWith("$path#") }
        materialDefaults.keys.removeAll { it == path || it.startsWith("$path#") }
        skinnedPartDefaults.keys.removeAll { it.startsWith("$PRIMITIVE_MESH_PREFIX$path#") }
        skinnedPartTextures.keys.removeAll { it.startsWith("$path#") }
    }

    /**
     * Synchronous fallback for the existing editor placement seam. New file-backed callers use
     * [preload] so JSON sidecars are resolved first; picker callers that cannot suspend still get
     * the embedded/data-URI behavior rather than an API that returns nullable bytes.
     */
    fun preloadBytes(path: String, bytes: ByteArray) {
        preload(path, bytes, emptyMap())
    }

    /**
     * Decodes glTF image payloads before the synchronous scene asset library asks for a GPU
     * material. The editor calls this from its coroutine-owned placement path because image
     * decoding is suspendable on every supported platform.
     *
     * A single scene material is required for the current one-mesh scene contract. If primitives
     * disagree about material inputs, geometry remains placeable with baked vertex factors and
     * no misleading texture is applied to the wrong primitive.
     */
    suspend fun preloadMaterials(path: String, bytes: ByteArray) {
        if (path in loadedMaterialSlots) return
        val skinned = loadedScenes[path] ?: parseSkinnedScene(path, bytes)?.also { loadedScenes[path] = it }
        if (skinned != null && skinned.skinnedNodes.isNotEmpty()) preloadSkinnedParts(path) else preloadStaticSlots(path, bytes)
    }

    private suspend fun preloadStaticSlots(path: String, bytes: ByteArray) {
        val scene = parseStaticScene(path, bytes)
        val primitives = scene.meshes.flatMap { it.primitives }
        if (primitives.isEmpty()) return

        val slots = mutableListOf<GltfMaterialSlot>()
        primitives.forEachIndexed { index, primitive ->
            val materialKey = if (primitives.size == 1) path else "$path#$index"
            val material = primitive.toLoadedMaterial()
            material?.let { loadedMaterials[materialKey] = it }
            val meshKey = if (primitives.size == 1) path else "$PRIMITIVE_MESH_PREFIX$path#$index"
            loadedStaticMeshes[meshKey] = listOf(primitive).toStaticGeometry(textured = material != null)
            slots += GltfMaterialSlot(
                mesh = meshKey,
                material = material?.let { "gltf-material:$materialKey" } ?: "lit-shadow",
                parameters = material?.parameters,
            )
        }
        loadedMaterialSlots[path] = slots
    }

    private suspend fun preloadSkinnedParts(path: String) {
        loadedMaterialSlots[path] = parts(path).orEmpty().mapIndexed { index, part ->
            val key = "$path#$index"
            when (part) {
                is GltfPart.Skinned -> {
                    val mesh = part.mesh
                    mesh.baseColorImageBytes?.takeIf { mesh.isTextured }?.let { skinnedPartTextures[key] = decodeTexture(it) }
                    GltfMaterialSlot(
                        mesh = "$PRIMITIVE_MESH_PREFIX$key",
                        material = if (mesh.isTextured) "gltf-material:$key" else "skinned-material",
                        parameters = mesh.toMaterialParameters(),
                    )
                }
                // Drawn and textured as a static model's primitive is, so it takes the same slot.
                is GltfPart.Static -> {
                    val material = part.primitive.toLoadedMaterial()
                    material?.let { loadedMaterials[key] = it }
                    GltfMaterialSlot(
                        mesh = "$PRIMITIVE_MESH_PREFIX$key",
                        material = material?.let { "gltf-material:$key" } ?: "lit-shadow",
                        parameters = material?.parameters,
                    )
                }
            }
        }
    }

    /** The parts of the skinned model at [path], built once from its loaded scene; null when none is loaded. */
    private fun parts(path: String): List<GltfPart>? =
        loadedScenes[path]?.let { scene -> loadedParts.getOrPut(path) { scene.parts() } }

    private fun part(name: String): GltfPart? = name.takeIf { it.startsWith(PRIMITIVE_MESH_PREFIX) }
        ?.let { parts(modelPath(it))?.getOrNull(it.substringAfterLast('#').toIntOrNull() ?: -1) }

    /** The geometry the part [name] draws, or null when it names no part of a skinned model. */
    internal fun partGeometry(name: String): MeshGeometry? = when (val part = part(name)) {
        null -> null
        is GltfPart.Skinned -> part.mesh.let { mesh ->
            if (mesh.isTextured) {
                MeshGeometry(mesh.toInterleavedPositionNormalColorUvSkin(), mesh.indices, format = VertexFormat.PositionNormalColorUvSkin)
            } else {
                MeshGeometry(mesh.toInterleavedSkinned(), mesh.indices, format = VertexFormat.PositionNormalColorSkin)
            }
        }
        is GltfPart.Static -> listOf(part.primitive).toStaticGeometry(textured = name.removePrefix(PRIMITIVE_MESH_PREFIX) in loadedMaterials)
    }

    /**
     * Returns the material key to persist in a placed scene descriptor.
     *
     * @param path The glTF or GLB asset path.
     * @return Material identifier string to use in scene definitions.
     */
    fun materialName(path: String): String = if (path in loadedMaterials) {
        "gltf-material:$path"
    } else {
        "lit-shadow"
    }

    /**
     * Retrieves the parsed material parameters associated with the specified model asset path.
     *
     * @param path The glTF or GLB asset path.
     * @return The material parameters, or null if no material was loaded for the path.
     */
    fun materialParameters(path: String): GltfMaterialParameters? = loadedMaterials[path]?.parameters

    /**
     * Per-primitive render inputs for imported models with more than one glTF material.
     *
     * @param path The glTF or GLB asset path.
     * @return List of material slots for the model, or an empty list if none exist.
     */
    fun materialSlots(path: String): List<GltfMaterialSlot> = loadedMaterialSlots[path].orEmpty()

    /**
     * The model file behind [meshName]: a `gltf-primitive:<path>#<index>` mesh is one primitive of `<path>`.
     *
     * @param meshName Name or compound identifier of the mesh.
     * @return Base model file path.
     */
    fun modelPath(meshName: String): String = if (meshName.startsWith(PRIMITIVE_MESH_PREFIX)) {
        meshName.removePrefix(PRIMITIVE_MESH_PREFIX).substringBeforeLast('#')
    } else {
        meshName
    }

    override fun canResolveMesh(name: String): Boolean = modelPath(name).let { path ->
        path.endsWith(".gltf", ignoreCase = true) || path.endsWith(".glb", ignoreCase = true)
    }

    override fun createMesh(runtime: SceneAppLifecycleRuntime, name: String): Mesh? {
        val geometry = partGeometry(name) ?: loadedScenes[name]
            ?.firstSkinnedAsset()
            ?.let { skinnedAsset ->
                MeshGeometry(
                    skinnedAsset.mesh.toInterleavedSkinned(),
                    skinnedAsset.mesh.indices,
                    format = VertexFormat.PositionNormalColorSkin,
                )
            } ?: loadedStaticMeshes[name] ?: error(
            "glTF asset '$name' has not been preloaded. Ensure the scene or plugin preloaded it.",
        )
        meshBoundsRegistrar(name, geometry)
        return runtime.renderer.createMesh(geometry)
    }

    override fun canResolveMaterial(name: String): Boolean =
        name == "skinned-material" || name.startsWith("gltf-material:")

    override fun createMaterial(runtime: SceneAppLifecycleRuntime, name: String): Material? = when {
        name == "skinned-material" -> runtime.renderer.createMaterial(SkinnedUniformLayout)
        name.startsWith("gltf-material:") && name.removePrefix("gltf-material:") in skinnedPartTextures ->
            runtime.renderer.createMaterial(SkinnedUniformLayout, texture = skinnedPartTextures.getValue(name.removePrefix("gltf-material:")))
        name.startsWith("gltf-material:") -> loadedMaterials[name.removePrefix("gltf-material:")]?.let { loaded ->
            runtime.renderer.createMaterial(TexturedUniformLayout, texture = loaded.texture, pbrTextures = loaded.pbrTextures)
        }
        else -> null
    }

    private val materialDefaults = HashMap<String, PbrMaterial>()
    private val skinnedPartDefaults = HashMap<String, PbrMaterial>()

    /** A glTF material's own factors and alpha mode, one shared instance per material. */
    override fun materialDefaults(name: String): PbrMaterial? {
        val path = name.takeIf { it.startsWith("gltf-material:") }?.removePrefix("gltf-material:")
        val parameters = path?.let { loadedMaterials[it]?.parameters } ?: return null
        return materialDefaults.getOrPut(path) { parameters.toPbrMaterial() }
    }

    /**
     * A skinned part's own factors, one shared instance per part. A part is told apart by its mesh:
     * the untextured ones all draw with the one `skinned-material`, so the material name alone
     * cannot say whose factors to use. Anything that is not a skinned part, a static one included,
     * falls back to its material's.
     */
    override fun materialDefaults(mesh: String, material: String): PbrMaterial? {
        val parameters = skinnedPartParameters(mesh) ?: return materialDefaults(material)
        return skinnedPartDefaults.getOrPut(mesh) { parameters.toPbrMaterial() }
    }

    private fun skinnedPartParameters(mesh: String): GltfMaterialParameters? {
        if (part(mesh) !is GltfPart.Skinned) return null
        return loadedMaterialSlots[modelPath(mesh)]?.firstOrNull { it.mesh == mesh }?.parameters
    }
}

private val log = Logger("scene.gltf")

private const val PRIMITIVE_MESH_PREFIX = "gltf-primitive:"

/**
 * Associates a primitive mesh with its bound glTF material and parameters.
 *
 * @property mesh Mesh identifier for the primitive part.
 * @property material Material key bound to the primitive mesh.
 * @property parameters PBR material parameters applied to this primitive slot, or null if default.
 */
data class GltfMaterialSlot(
    val mesh: String,
    val material: String,
    val parameters: GltfMaterialParameters?,
)

/**
 * PBR material rendering parameters extracted from a glTF material definition.
 *
 * @property metallic Metallic factor normalized in `[0f, 1f]`.
 * @property roughness Roughness factor normalized in `[0f, 1f]`.
 * @property baseColorFactor Multiplier applied to the base color texture or vertex colors.
 * @property emissiveFactor Color emitted by the surface independently of light sources.
 * @property alphaMode Alpha blending mode specifying how transparency is calculated.
 */
data class GltfMaterialParameters(
    val metallic: Float,
    val roughness: Float,
    val baseColorFactor: Color,
    val emissiveFactor: Color,
    val alphaMode: AlphaMode,
)

private data class LoadedGltfMaterial(
    val texture: TextureAsset,
    val pbrTextures: PbrTextureSet?,
    val parameters: GltfMaterialParameters,
)

private suspend fun LoadedPrimitive.toLoadedMaterial(): LoadedGltfMaterial? {
    val baseColorBytes = baseColorImageBytes ?: return null
    val pbrTextures = PbrTextureSet(
        metallicRoughness = metallicRoughnessImageBytes?.let { decodeTexture(it) },
        normal = normalImageBytes?.let { decodeTexture(it) },
        occlusion = occlusionImageBytes?.let { decodeTexture(it) },
        emissive = emissiveImageBytes?.let { decodeTexture(it) },
    )
    val hasPbrTextures = pbrTextures.metallicRoughness != null ||
        pbrTextures.normal != null ||
        pbrTextures.occlusion != null ||
        pbrTextures.emissive != null
    return LoadedGltfMaterial(
        texture = decodeTexture(baseColorBytes),
        pbrTextures = pbrTextures.takeIf { hasPbrTextures },
        parameters = toMaterialParameters(),
    )
}

private suspend fun decodeTexture(bytes: ByteArray): TextureAsset {
    val bitmap = createBitmap(bytes)
    return TextureAsset(bitmap.toRgba8Bytes(), bitmap.width, bitmap.height)
}

/** One drawable piece of a skinned model: deformed by the model's pose, or fixed in place. */
private sealed interface GltfPart {
    /** A primitive drawn with the joint palette: a skinned mesh's, or a rigid node's bound to its joint. */
    class Skinned(val mesh: GltfMesh) : GltfPart

    /** A rigid node's primitive that no joint carries, at its node's rest transform. */
    class Static(val primitive: LoadedPrimitive) : GltfPart
}

/**
 * The model's parts, in the order [GltfAssetResolver] numbers them: each skinned node's first
 * primitive, then those nodes' other primitives, then each rigid node's. Every skinned part draws
 * with the first skin's palette, so a rigid node is bound into that skin; none for a file with no
 * skinned node, which a static model's slots draw instead.
 */
private fun LoadedSkinnedScene.parts(): List<GltfPart> {
    val first = skinnedNodes.firstOrNull() ?: return emptyList()
    val skin = skins[first.skinIndex]
    val firstPrimitives = skinnedNodes.map { GltfPart.Skinned(primitives[it.meshIndex].first()) }
    val otherPrimitives = skinnedNodes.flatMap { node -> primitives[node.meshIndex].drop(1).map { GltfPart.Skinned(it) } }
    val rigid = rigidNodes.flatMap { node ->
        bindRigidNode(node, skin)?.map { GltfPart.Skinned(it) }
            ?: restTransform(node.boneIndex).let { rest -> primitives[node.meshIndex].map { GltfPart.Static(it.toLoadedPrimitive(rest)) } }
    }
    return firstPrimitives + otherPrimitives + rigid
}

private val GltfMesh.isTextured: Boolean get() = uvs != null && baseColorImageBytes != null

/** The skinned scene in a `.gltf` [bytes], or null for a `.glb` or a file this parser cannot skin. */
private suspend fun GltfAssetResolver.parseSkinnedScene(path: String, bytes: ByteArray): LoadedSkinnedScene? {
    if (bytes.isGlb()) return null
    val json = bytes.decodeToString()
    val external = GltfParser.loadExternalResources(json, AssetPath(path), AssetSource { assetPath -> readAsset(assetPath) }).getOrThrow()
    return runCatching { GltfParser.parseSkinned(json, external) }.getOrNull()
}

private suspend fun GltfAssetResolver.parseStaticScene(path: String, bytes: ByteArray): LoadedScene =
    parseGltfScene(path, bytes, AssetSource { assetPath -> readAsset(assetPath) })

/**
 * Reads the model at [path] from [source] as a [LoadedScene]: a `.glb`, or a `.gltf` with its `.bin`
 * and image sidecars, which are resolved next to it.
 */
suspend fun readGltfScene(path: String, source: AssetSource): LoadedScene =
    parseGltfScene(path, source.read(AssetPath(path)).getOrThrow(), source)

private suspend fun parseGltfScene(path: String, bytes: ByteArray, source: AssetSource): LoadedScene =
    if (bytes.isGlb()) {
        GltfParser.parseScene(bytes)
    } else {
        val json = bytes.decodeToString()
        GltfParser.parseScene(json.toEmbeddedGlb(), GltfParser.loadExternalResources(json, AssetPath(path), source).getOrThrow())
    }

private fun ByteArray.isGlb(): Boolean =
    size >= 4 &&
        this[0] == 'g'.code.toByte() &&
        this[1] == 'l'.code.toByte() &&
        this[2] == 'T'.code.toByte() &&
        this[3] == 'F'.code.toByte()

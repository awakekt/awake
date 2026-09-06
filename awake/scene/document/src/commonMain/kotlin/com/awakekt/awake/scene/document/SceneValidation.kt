/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.document

data class SceneValidationIssue(
    val path: String,
    val message: String,
)

class SceneValidationException(
    val issues: List<SceneValidationIssue>,
) : IllegalArgumentException(
    issues.joinToString(
        prefix = "Invalid scene document:\n",
        separator = "\n",
    ) { issue -> "- ${issue.path}: ${issue.message}" },
)

object SceneValidator {
    fun validate(
        document: SceneDocument,
        extensions: SceneExtensionRegistry? = null,
    ): List<SceneValidationIssue> {
        val issues = ArrayList<SceneValidationIssue>()
        val namedPaths = LinkedHashMap<String, String>()
        document.nodes.forEachIndexed { index, node ->
            validateNode(node, path = nodePath(node.name, index, null), issues = issues, namedPaths = namedPaths)
        }
        extensions?.validate(document)?.forEach { message ->
            if (message.severity == SceneExtensionValidationSeverity.Error) {
                issues += SceneValidationIssue("extensions", message.message)
            }
        }
        return issues
    }

    fun requireValid(
        document: SceneDocument,
        extensions: SceneExtensionRegistry? = null,
    ) {
        val issues = validate(document, extensions)
        if (issues.isNotEmpty()) {
            throw SceneValidationException(issues)
        }
    }

    private fun validateNode(
        node: SceneNode,
        path: String,
        issues: MutableList<SceneValidationIssue>,
        namedPaths: MutableMap<String, String>,
    ) {
        node.name?.takeIf { it.isNotBlank() }?.let { name ->
            val previous = namedPaths[name]
            if (previous == null) {
                namedPaths[name] = path
            } else {
                issues += SceneValidationIssue(path, "duplicate node name '$name' already used at $previous")
            }
        }

        node.components.filterIsInstance<SceneCamera>().size.takeIf { it > 1 }?.let { count ->
            issues += SceneValidationIssue(path, "node declares $count cameras, expected at most 1")
        }

        node.components.forEach { component -> validateComponent(component, path, issues) }

        node.children.forEachIndexed { index, child ->
            validateNode(child, nodePath(child.name, index, path), issues, namedPaths)
        }
    }
}

private fun validateComponent(
    component: SceneComponent,
    path: String,
    issues: MutableList<SceneValidationIssue>,
) {
    when (component) {
        is SceneMeshRenderer -> validateMeshRenderer(component, path, issues)
        is SceneCamera -> validateCamera(component, path, issues)
        is ScenePbrMaterial -> validatePbrMaterial(component, path, issues)
        is SceneSpinControl -> validateSpinControl(component, path, issues)
        is ScenePatrol -> validatePatrol(component, path, issues)
        is SceneChase -> validateRoute(component.speed, component.repathInterval, "chase", path, issues)
        is SceneFlee -> validateFlee(component, path, issues)
        is ScenePrefabLink -> validatePrefabLink(component, path, issues)
        is SceneCustomComponent -> validateCustomComponent(component, path, issues)
        is SceneLight -> Unit
    }
}

private fun validateMeshRenderer(
    component: SceneMeshRenderer,
    path: String,
    issues: MutableList<SceneValidationIssue>,
) {
    if (component.mesh.isBlank()) {
        issues += SceneValidationIssue(path, "meshRenderer.mesh must not be blank")
    }
    if (component.material.isBlank()) {
        issues += SceneValidationIssue(path, "meshRenderer.material must not be blank")
    }
}

private fun validateCamera(
    component: SceneCamera,
    path: String,
    issues: MutableList<SceneValidationIssue>,
) {
    if (component.near <= 0f) {
        issues += SceneValidationIssue(path, "camera.near must be > 0")
    }
    if (component.far <= component.near) {
        issues += SceneValidationIssue(path, "camera.far must be greater than camera.near")
    }
    if (component.fovYDegrees <= 0f || component.fovYDegrees >= 180f) {
        issues += SceneValidationIssue(path, "camera.fovYDegrees must be between 0 and 180")
    }
}

private fun validatePbrMaterial(
    component: ScenePbrMaterial,
    path: String,
    issues: MutableList<SceneValidationIssue>,
) {
    if (component.metallic !in 0f..1f) {
        issues += SceneValidationIssue(path, "pbrMaterial.metallic must be within 0..1")
    }
    if (component.roughness !in 0f..1f) {
        issues += SceneValidationIssue(path, "pbrMaterial.roughness must be within 0..1")
    }
}

private fun validateSpinControl(
    component: SceneSpinControl,
    path: String,
    issues: MutableList<SceneValidationIssue>,
) {
    if (component.speed < 0f) {
        issues += SceneValidationIssue(path, "spinControl.speed must not be negative")
    }
}

private fun validatePrefabLink(
    component: ScenePrefabLink,
    path: String,
    issues: MutableList<SceneValidationIssue>,
) {
    if (component.prefabGuid.isBlank()) {
        issues += SceneValidationIssue(path, "prefabLink.prefabGuid must not be blank")
    }
}

private fun validateCustomComponent(
    component: SceneCustomComponent,
    path: String,
    issues: MutableList<SceneValidationIssue>,
) {
    if (component.type.isBlank()) {
        issues += SceneValidationIssue(path, "custom.type must not be blank")
    }
}

private fun nodePath(name: String?, index: Int, parent: String?): String {
    val segment = name?.takeIf { it.isNotBlank() } ?: "#$index"
    return if (parent == null) segment else "$parent/$segment"
}

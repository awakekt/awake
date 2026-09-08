/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.document

/**
 * Validation issue detected in a scene document.
 *
 * @property path Scene node path where the issue was detected (e.g. `root/camera`).
 * @property message Human-readable validation issue description.
 */
data class SceneValidationIssue(
    val path: String,
    val message: String,
)

/**
 * Exception thrown when scene document validation fails.
 *
 * @property issues List of detected validation issues.
 */
class SceneValidationException(
    val issues: List<SceneValidationIssue>,
) : IllegalArgumentException(
    issues.joinToString(
        prefix = "Invalid scene document:\n",
        separator = "\n",
    ) { issue -> "- ${issue.path}: ${issue.message}" },
)

/**
 * Validator checking scene documents for structural integrity, unique node names, and component constraints.
 */
object SceneValidator {
    /** Validates [document] and returns a list of detected validation issues. */
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

    /** Validates [document] and throws a [SceneValidationException] if any validation issues are detected. */
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

        node.components.groupBy { it::class }.forEach { (componentClass, list) ->
            if (list.size > 1 && !list.first().allowsMultiplePerNode) {
                val typeName = componentClass.simpleName ?: "Component"
                issues += SceneValidationIssue(path, "node declares ${list.size} $typeName instances, expected at most 1")
            }
        }

        node.components.forEach { component ->
            issues += component.validate(path)
        }

        node.children.forEachIndexed { index, child ->
            validateNode(child, nodePath(child.name, index, path), issues, namedPaths)
        }
    }
}

private fun nodePath(name: String?, index: Int, parent: String?): String {
    val segment = name?.takeIf { it.isNotBlank() } ?: "#$index"
    return if (parent == null) segment else "$parent/$segment"
}

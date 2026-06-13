package io.github.sgrishchenko.karakum.extension.nameResolvers

import io.github.sgrishchenko.karakum.configuration.NamespaceStrategy
import io.github.sgrishchenko.karakum.configuration.`object`
import io.github.sgrishchenko.karakum.configuration.`package`
import io.github.sgrishchenko.karakum.extension.Context
import io.github.sgrishchenko.karakum.extension.plugins.namespaceInfoServiceKey
import io.github.sgrishchenko.karakum.extension.plugins.typeScriptServiceKey
import io.github.sgrishchenko.karakum.util.getParentOrNull
import typescript.*

fun resolveNamespacePrefix(node: Node, context: Context): String {
    val typeScriptService = context.lookupService(typeScriptServiceKey) ?: return ""
    val namespaceInfoService = context.lookupService(namespaceInfoServiceKey) ?: return ""

    val getParent = { it: Node ->
        typeScriptService.getParent(it) ?: it.getParentOrNull()
    }

    val qualifiers = mutableListOf<String>()
    var current: Node? = getParent(node)

    while (current != null) {
        if (isModuleDeclaration(current)) {
            val strategy = namespaceInfoService.resolveNamespaceStrategy(current)
            if (strategy == NamespaceStrategy.`object`) {
                val name = current.name
                val simpleName = when {
                    isIdentifier(name) -> name.text
                    isStringLiteral(name) -> name.text
                    else -> ""
                }
                if (simpleName.isNotEmpty()) {
                    qualifiers.add(0, simpleName)
                }
            } else if (strategy == NamespaceStrategy.`package`) {
                break
            }
        }
        current = getParent(current)
    }

    return qualifiers.joinToString("")
}

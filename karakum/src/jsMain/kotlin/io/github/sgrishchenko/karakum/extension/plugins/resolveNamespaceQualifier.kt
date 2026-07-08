package io.github.sgrishchenko.karakum.extension.plugins

import io.github.sgrishchenko.karakum.configuration.NamespaceStrategy
import io.github.sgrishchenko.karakum.configuration.`object`
import io.github.sgrishchenko.karakum.configuration.`package`
import io.github.sgrishchenko.karakum.extension.Context
import js.numbers.contains
import typescript.*

fun resolveNamespaceQualifier(identifier: Node, context: Context): String? {
    val typeScriptService = context.lookupService(typeScriptServiceKey) ?: return null
    val namespaceInfoService = context.lookupService(namespaceInfoServiceKey) ?: return null
    val typeChecker = typeScriptService.program.getTypeChecker()

    var symbol = typeChecker.getSymbolAtLocation(identifier) ?: return null

    if (SymbolFlags.Alias in symbol.flags) {
        symbol = typeChecker.getAliasedSymbol(symbol)
    }

    val declaration = symbol.valueDeclaration
        ?: symbol.declarations?.firstOrNull()
        ?: return null

    if (isTypeParameterDeclaration(declaration)) return null

    val qualifiers = mutableListOf<String>()
    var current: Node? = typeScriptService.getParent(declaration)

    while (current != null) {
        if (isModuleDeclaration(current)) {
            val namespaceStrategy = namespaceInfoService.resolveNamespaceStrategy(current)
            if (namespaceStrategy == NamespaceStrategy.`object`) {
                val namespaceName = current.name
                val simpleName = when {
                    isIdentifier(namespaceName) -> namespaceName.text
                    isStringLiteral(namespaceName) -> namespaceName.text
                    else -> ""
                }
                if (simpleName.isNotEmpty()) {
                    qualifiers.add(0, simpleName)
                }
            } else if (namespaceStrategy == NamespaceStrategy.`package`) {
                break
            }
        }
        current = typeScriptService.getParent(current)
    }

    return if (qualifiers.isNotEmpty()) qualifiers.joinToString(".") else null
}

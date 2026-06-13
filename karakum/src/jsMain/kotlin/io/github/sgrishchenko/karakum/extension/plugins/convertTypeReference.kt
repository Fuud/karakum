package io.github.sgrishchenko.karakum.extension.plugins

import io.github.sgrishchenko.karakum.extension.createPlugin
import js.numbers.contains
import typescript.*

val convertTypeReference = createPlugin plugin@{ node, context, render ->
    if (!isTypeReferenceNode(node)) return@plugin null

    val checkCoverageService = context.lookupService(checkCoverageServiceKey)
    checkCoverageService?.cover(node)

    val typeName = node.typeName
    val renderedTypeName = when {
        isIdentifier(typeName) -> {
            val result = resolveNamespaceQualifierAndRegisterImport(typeName, context)
            if (result.qualifier != null) "${result.qualifier}.${typeName.text}" else render(typeName)
        }
        isQualifiedName(typeName) -> {
            // When the left part of a QualifiedName resolves to an enum declaration
            // (e.g., MessageType.DEBUG in `type: MessageType.DEBUG`), render just the
            // enum type name because in Kotlin, enum members are companion object vals, not types.
            val left = typeName.left
            val right = typeName.right
            val typeScriptService = context.lookupService(typeScriptServiceKey)
            val typeChecker = typeScriptService?.program?.getTypeChecker()
            var leftSymbol = typeChecker?.getSymbolAtLocation(left)

            if (typeChecker != null && leftSymbol != null && SymbolFlags.Alias in leftSymbol.flags) {
                leftSymbol = typeChecker.getAliasedSymbol(leftSymbol)
            }

            val isEnumReference = leftSymbol?.valueDeclaration?.let { isEnumDeclaration(it) } == true
                || leftSymbol?.declarations?.any { isEnumDeclaration(it) } == true

            if (isEnumReference) {
                checkCoverageService?.cover(typeName)
                checkCoverageService?.cover(right)
                render(left)
            } else {
                // Register demand-driven import for the right-hand type.
                // When an import is registered, use just the simple name since the
                // import makes the qualifier unnecessary (and the namespace object
                // like "Vmoji" won't exist in Kotlin scope).
                val result = resolveNamespaceQualifierAndRegisterImport(right, context)
                if (result.dynamicImportRegistered) right.text else render(typeName)
            }
        }
        else -> render(typeName)
    }

    renderedTypeName + convertNodeWithTypeArguments(node, context, render)
}

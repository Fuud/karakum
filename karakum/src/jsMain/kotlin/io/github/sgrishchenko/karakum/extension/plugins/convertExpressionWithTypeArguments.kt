package io.github.sgrishchenko.karakum.extension.plugins

import io.github.sgrishchenko.karakum.extension.createPlugin
import js.numbers.contains
import typescript.*

val convertExpressionWithTypeArguments = createPlugin plugin@{ node, context, render ->
    if (!isExpressionWithTypeArguments(node)) return@plugin null

    val checkCoverageService = context.lookupService(checkCoverageServiceKey)
    checkCoverageService?.cover(node)

    val expression = node.expression
    val renderedExpression = when {
        isIdentifier(expression) -> {
            val result = resolveNamespaceQualifierAndRegisterImport(expression, context)
            if (result.qualifier != null) "${result.qualifier}.${expression.text}" else render(expression)
        }
        isPropertyAccessExpression(expression) -> {
            // When a PropertyAccessExpression in a heritage clause references an enum member
            // (e.g., MessageType.DEBUG in `extends MessageType.DEBUG`), render just the
            // enum type name because in Kotlin, enum members are companion object vals, not types.
            val typeScriptService = context.lookupService(typeScriptServiceKey)
            val typeChecker = typeScriptService?.program?.getTypeChecker()
            var leftSymbol = typeChecker?.getSymbolAtLocation(expression.expression)

            if (typeChecker != null && leftSymbol != null && SymbolFlags.Alias in leftSymbol.flags) {
                leftSymbol = typeChecker.getAliasedSymbol(leftSymbol)
            }

            val isEnumReference = leftSymbol?.valueDeclaration?.let { isEnumDeclaration(it) } == true
                || leftSymbol?.declarations?.any { isEnumDeclaration(it) } == true

            if (isEnumReference) {
                checkCoverageService?.cover(expression)
                checkCoverageService?.cover(expression.name)
                render(expression.expression)
            } else {
                render(expression)
            }
        }
        else -> render(expression)
    }

    renderedExpression + convertNodeWithTypeArguments(node, context, render)
}

package io.github.sgrishchenko.karakum.extension.plugins

import io.github.sgrishchenko.karakum.extension.createPlugin
import js.numbers.contains
import typescript.*

val convertLiteralType = createPlugin plugin@{ node, context, render ->
    if (!isLiteralTypeNode(node)) return@plugin null

    val checkCoverageService = context.lookupService(checkCoverageServiceKey)
    checkCoverageService?.cover(node)

    val literal = node.literal

    // When the literal is a PropertyAccessExpression referencing an enum member
    // (e.g., MessageType.DEBUG in `type: MessageType.DEBUG`), render just the
    // enum type name because in Kotlin, enum members are companion object vals, not types.
    if (isPropertyAccessExpression(literal)) {
        val typeScriptService = context.lookupService(typeScriptServiceKey)
        val typeChecker = typeScriptService?.program?.getTypeChecker()
        var expressionSymbol = typeChecker?.getSymbolAtLocation(literal.expression)

        if (typeChecker != null && expressionSymbol != null && SymbolFlags.Alias in expressionSymbol.flags) {
            expressionSymbol = typeChecker.getAliasedSymbol(expressionSymbol)
        }

        val isEnumReference = expressionSymbol?.valueDeclaration?.let { isEnumDeclaration(it) } == true
            || expressionSymbol?.declarations?.any { isEnumDeclaration(it) } == true

        if (isEnumReference) {
            checkCoverageService?.cover(literal)
            checkCoverageService?.cover(literal.name)
            return@plugin render(literal.expression)
        }
    }

    render(node.literal)
}

package io.github.sgrishchenko.karakum.extension.plugins

import io.github.sgrishchenko.karakum.extension.Context
import io.github.sgrishchenko.karakum.extension.createPlugin
import io.github.sgrishchenko.karakum.util.camelize
import io.github.sgrishchenko.karakum.util.getParentOrNull
import io.github.sgrishchenko.karakum.util.isKebab
import io.github.sgrishchenko.karakum.util.isValidIdentifier
import js.numbers.contains
import typescript.*

fun createKebabAnnotation(node: Node, context: Context? = null): String {
    if (isStringLiteral(node) && node.text == "") {
        return "@JsName(\"\")"
    }

    if (
        isStringLiteral(node)
        && !isValidIdentifier(node.text)
        && isKebab(node.text)
        && isValidIdentifier(camelize(node.text))
    ) {
        return "@JsName(\"${node.text}\")"
    }

    if (isComputedPropertyName(node) && context != null) {
        val resolved = resolveComputedPropertyName(node.unsafeCast<ComputedPropertyName>(), context) ?: return ""
        if (!isValidIdentifier(resolved) && isKebab(resolved) && isValidIdentifier(camelize(resolved))) {
            return "@JsName(\"$resolved\")"
        }
    }

    return ""
}

private fun resolveComputedPropertyName(node: ComputedPropertyName, context: Context): String? {
    val typeScriptService = context.lookupService(typeScriptServiceKey) ?: return null
    val typeChecker = typeScriptService.program.getTypeChecker()

    val expression = node.expression

    if (isPropertyAccessExpression(expression)) {
        var expressionSymbol = typeChecker?.getSymbolAtLocation(expression)

        if (typeChecker != null && expressionSymbol != null && SymbolFlags.Alias in expressionSymbol.flags) {
            expressionSymbol = typeChecker.getAliasedSymbol(expressionSymbol)
        }

        val declaration = expressionSymbol?.valueDeclaration
        if (declaration != null && isEnumMember(declaration)) {
            val constantValue = typeChecker?.getConstantValue(declaration) ?: return null
            return constantValue.toString()
        }
    }

    if (isStringLiteral(expression)) {
        return expression.text
    }

    if (isNumericLiteral(expression)) {
        return expression.text
    }

    return null
}

fun convertMemberNameLiteral(node: StringLiteral): String {
    return if (node.text == "") {
        "`_`"
    } else if (isValidIdentifier(node.text)) {
        node.text
    } else if ("$" in node.text) {
        node.text
    } else if (
        isKebab(node.text)
        && isValidIdentifier(camelize(node.text))
    ) {
        camelize(node.text)
    } else {
        "`${node.text}`"
    }
}

private fun convertMemberNameLiteralFromText(text: String): String {
    return if (text == "") {
        "`_`"
    } else if (isValidIdentifier(text)) {
        text
    } else if ("$" in text) {
        text
    } else if (
        isKebab(text)
        && isValidIdentifier(camelize(text))
    ) {
        camelize(text)
    } else {
        "`$text`"
    }
}

val convertMemberName = createPlugin plugin@{ node, context, _ ->
    if (isStringLiteral(node) || isNumericLiteral(node) || isComputedPropertyName(node)) {
        val parent = node.getParentOrNull() ?: return@plugin null

        if (
            (isPropertyDeclaration(parent) && parent.name === node)
            || (isPropertySignature(parent) && parent.name === node)

            || (isMethodDeclaration(parent) && parent.name === node)
            || (isMethodSignature(parent) && parent.name === node)

            || (isGetAccessor(parent) && parent.name === node)
            || (isSetAccessor(parent) && parent.name === node)

            || (isEnumMember(parent) && parent.name === node)
        ) {
            val checkCoverageService = context.lookupService(checkCoverageServiceKey)
            checkCoverageService?.cover(node)

            if (isNumericLiteral(node)) return@plugin "`${node.text}`"
            if (isStringLiteral(node)) return@plugin convertMemberNameLiteral(node)
            if (isComputedPropertyName(node)) {
                val resolved = resolveComputedPropertyName(node.unsafeCast<ComputedPropertyName>(), context) ?: return@plugin null
                return@plugin convertMemberNameLiteralFromText(resolved)
            }
        }
    }

    null
}

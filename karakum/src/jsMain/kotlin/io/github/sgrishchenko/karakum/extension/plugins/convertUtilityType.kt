package io.github.sgrishchenko.karakum.extension.plugins

import io.github.sgrishchenko.karakum.extension.Context
import io.github.sgrishchenko.karakum.extension.Render
import io.github.sgrishchenko.karakum.extension.createPlugin
import io.github.sgrishchenko.karakum.extension.isBuiltin
import io.github.sgrishchenko.karakum.util.capitalize
import typescript.ExpressionWithTypeArguments
import typescript.Identifier
import typescript.LiteralTypeNode
import typescript.Node
import typescript.TypeChecker
import typescript.TypeReferenceNode
import typescript.UnionTypeNode
import typescript.asArray
import typescript.isConditionalTypeNode
import typescript.isExpressionWithTypeArguments
import typescript.isIdentifier
import typescript.isIndexedAccessTypeNode
import typescript.isLiteralTypeNode
import typescript.isQualifiedName
import typescript.isStringLiteral
import typescript.isTypeQueryNode
import typescript.isTypeReferenceNode
import typescript.isUnionTypeNode

private val utilityTypeNames = setOf(
    "Partial",
    "Required",
    "Pick",
    "Omit",
    "Readonly",
    "Exclude",
    "Extract",
    "NonNullable",
)

private fun isUtilityType(node: TypeReferenceNode, context: Context): Boolean {
    val typeName = node.typeName
    if (!isIdentifier(typeName)) return false
    if (typeName.text !in utilityTypeNames) return false
    return isBuiltin(typeName, context)
}

private fun isUtilityTypeExpression(node: ExpressionWithTypeArguments, context: Context): Boolean {
    val expression = node.expression
    if (!isIdentifier(expression)) return false
    if (expression.text !in utilityTypeNames) return false
    return isBuiltin(expression, context)
}

private fun getUtilityTypeName(node: Node): String? {
    val name = when {
        isTypeReferenceNode(node) -> (node as TypeReferenceNode).typeName
        isExpressionWithTypeArguments(node) -> (node as ExpressionWithTypeArguments).expression
        else -> return null
    }
    if (!isIdentifier(name)) return null
    val text = (name as Identifier).text
    return if (text in utilityTypeNames) text else null
}

private fun extractStringLiteralKeys(node: Node): List<String> {
    if (isLiteralTypeNode(node)) {
        val literal = (node as LiteralTypeNode).literal
        if (isStringLiteral(literal)) return listOf(literal.text)
        return emptyList()
    }
    if (isUnionTypeNode(node)) {
        return (node as UnionTypeNode).types.asArray()
            .flatMap { extractStringLiteralKeys(it) }
    }
    return emptyList()
}

private fun extractEntityName(node: Node): String? {
    if (isIdentifier(node)) return (node as Identifier).text
    if (isQualifiedName(node)) {
        val right = (node as typescript.QualifiedName).right
        return extractEntityName(right)
    }
    return null
}

private fun computeBaseName(node: Node, typeChecker: TypeChecker, context: Context): String? {
    if (isTypeReferenceNode(node) && isUtilityType(node, context)) {
        return computeUtilityTypeName(node, typeChecker, context)
    }

    if (isTypeReferenceNode(node)) {
        val typeName = (node as TypeReferenceNode).typeName
        extractEntityName(typeName)?.let { return it }
    }

    if (isTypeQueryNode(node)) {
        extractEntityName((node as typescript.TypeQueryNode).exprName)?.let { return it }
    }

    if (isIndexedAccessTypeNode(node)) {
        val indexed = node as typescript.IndexedAccessTypeNode
        if (isLiteralTypeNode(indexed.indexType)) {
            val literal = (indexed.indexType as LiteralTypeNode).literal
            if (isStringLiteral(literal)) return capitalize(literal.text)
        }
        computeBaseName(indexed.objectType, typeChecker, context)?.let { return it }
    }

    val type = typeChecker.getTypeFromTypeNode(node.unsafeCast<typescript.TypeNode>())
    val rawName = typeChecker.typeToString(type)
    val sanitized = rawName.filter { it.isLetterOrDigit() || it == '_' }
    if (sanitized.isEmpty()) return null
    return if (sanitized.first().isDigit()) "_$sanitized" else sanitized
}

private const val MAX_NAME_LENGTH = 64

private fun truncateWithHash(name: String): String {
    if (name.length <= MAX_NAME_LENGTH) return name
    val hash = name.hashCode().toUInt().toString(16).padStart(8, '0').takeLast(8)
    return name.take(MAX_NAME_LENGTH - 9) + "_" + hash
}

private fun computeUtilityTypeName(
    node: Node,
    typeChecker: TypeChecker,
    context: Context,
): String? {
    val utilityName = getUtilityTypeName(node) ?: return null
    val typeArgs = when {
        isTypeReferenceNode(node) -> (node as TypeReferenceNode).typeArguments?.asArray()
        isExpressionWithTypeArguments(node) -> (node as ExpressionWithTypeArguments).typeArguments?.asArray()
        else -> null
    }
    if (typeArgs.isNullOrEmpty()) return null

    val baseName = computeBaseName(typeArgs[0], typeChecker, context) ?: return null

    val keyPart = if ((utilityName == "Pick" || utilityName == "Omit") && typeArgs.size > 1) {
        val keys = extractStringLiteralKeys(typeArgs[1])
        keys.joinToString("") { capitalize(it) }
    } else {
        ""
    }

    return truncateWithHash("${baseName}${keyPart}${utilityName}")
}

private suspend fun resolveAndRender(node: Node, context: Context, render: Render<Node>): String? {
    val checkCoverageService = context.lookupService(checkCoverageServiceKey)
    val typeScriptService = context.lookupService(typeScriptServiceKey)
    val utilityTypeNameService = context.lookupService(utilityTypeNameServiceKey)
    val typeChecker = typeScriptService?.program?.getTypeChecker()

    checkCoverageService?.deepCover(node)

    val deterministicName = if (typeChecker != null) {
        computeUtilityTypeName(node, typeChecker, context)
    } else null

    val resolvedType = typeScriptService?.resolveType(node)

    if (resolvedType == null) return "Any /* ${typeScriptService?.printNode(node)} */"

    if (isConditionalTypeNode(resolvedType)) return "Any /* ${typeScriptService?.printNode(node)} */"

    if (deterministicName != null && utilityTypeNameService != null) {
        utilityTypeNameService.registerName(resolvedType, deterministicName)
    }

    return render(resolvedType)
}

val convertUtilityType = createPlugin plugin@{ node, context, render ->
    if (isTypeReferenceNode(node) && isUtilityType(node, context)) {
        return@plugin resolveAndRender(node, context, render)
    }

    if (isExpressionWithTypeArguments(node) && isUtilityTypeExpression(node, context)) {
        return@plugin resolveAndRender(node, context, render)
    }

    null
}

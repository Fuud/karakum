package io.github.sgrishchenko.karakum.extension.plugins

import io.github.sgrishchenko.karakum.extension.Context
import io.github.sgrishchenko.karakum.extension.Render
import io.github.sgrishchenko.karakum.extension.createPlugin
import io.github.sgrishchenko.karakum.extension.isBuiltin
import js.numbers.contains
import js.numbers.plus
import typescript.ExpressionWithTypeArguments
import typescript.Identifier
import typescript.Node
import typescript.NodeBuilderFlags
import typescript.Symbol
import typescript.SymbolFlags
import typescript.TypeReferenceNode
import typescript.isConditionalTypeNode
import typescript.isExpressionWithTypeArguments
import typescript.isIdentifier
import typescript.isTypeReferenceNode

private val utilityTypeNames = setOf(
    "Partial",
    "Required",
    "Pick",
    "Omit",
    "Readonly",
    "Exclude",
    "Extract",
    "NonNullable",
    "ReturnType",
    "Parameters",
    "InstanceType",
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

private fun isNonRenderableTypeAlias(node: TypeReferenceNode, context: Context): Boolean {
    val typeName = node.typeName
    if (!isIdentifier(typeName)) return false

    val nonRenderableService = context.lookupService(nonRenderableTypeAliasServiceKey) ?: return false
    val typeScriptService = context.lookupService(typeScriptServiceKey) ?: return false
    val typeChecker = typeScriptService.program.getTypeChecker()

    var symbol = typeChecker.getSymbolAtLocation(typeName) ?: return false
    if (SymbolFlags.Alias in symbol.flags) {
        symbol = typeChecker.getAliasedSymbol(symbol)
    }

    return nonRenderableService.isNonRenderable(symbol)
}

internal suspend fun resolveAndRender(node: Node, context: Context, render: Render<Node>): String? {
    val checkCoverageService = context.lookupService(checkCoverageServiceKey)
    val typeScriptService = context.lookupService(typeScriptServiceKey)
    val utilityTypeNameService = context.lookupService(utilityTypeNameServiceKey)
    val nameResolverService = context.requireService(nameResolverServiceKey)
    val typeChecker = typeScriptService?.program?.getTypeChecker()

    checkCoverageService?.deepCover(node)

    val type = typeChecker?.getTypeAtLocation(node)

    val existingName = if (type != null && utilityTypeNameService != null) {
        utilityTypeNameService.lookupName(type)
    } else null

    val resolvedType = typeScriptService?.resolveType(
        node.unsafeCast<typescript.TypeNode>(),
        context = context,
        flags = NodeBuilderFlags.NoTruncation + NodeBuilderFlags.InTypeAlias,
        walkProperties = true,
    )

    if (resolvedType == null) return "Any /* ${typeScriptService?.printNode(node)} */"

    if (isConditionalTypeNode(resolvedType)) return "Any /* ${typeScriptService?.printNode(node)} */"

    if (isTypeReferenceNode(resolvedType)) {
        val nonRenderableService = context.lookupService(nonRenderableTypeAliasServiceKey)
        if (nonRenderableService != null && typeChecker != null) {
            val typeName = (resolvedType as TypeReferenceNode).typeName
            if (isIdentifier(typeName)) {
                var symbol = typeChecker.getSymbolAtLocation(typeName)
                if (symbol != null && SymbolFlags.Alias in symbol.flags) {
                    symbol = typeChecker.getAliasedSymbol(symbol)
                }
                if (symbol != null && nonRenderableService.isNonRenderable(symbol)) {
                    return "Any /* ${typeScriptService?.printNode(node)} */"
                }
            }
        }
    }

    if (existingName != null) {
        nameResolverService.preregisterName(resolvedType, existingName)
    }

    val result = render(resolvedType)

    if (existingName == null && type != null && utilityTypeNameService != null) {
        val assignedName = nameResolverService.resolveName(resolvedType, context)
        utilityTypeNameService.registerName(type, assignedName)
    }

    return result
}

val convertUtilityType = createPlugin plugin@{ node, context, render ->
    if (isTypeReferenceNode(node) && isUtilityType(node, context)) {
        return@plugin resolveAndRender(node, context, render)
    }

    if (isTypeReferenceNode(node) && isNonRenderableTypeAlias(node, context)) {
        return@plugin resolveAndRender(node, context, render)
    }

    if (isExpressionWithTypeArguments(node) && isUtilityTypeExpression(node, context)) {
        return@plugin resolveAndRender(node, context, render)
    }

    null
}

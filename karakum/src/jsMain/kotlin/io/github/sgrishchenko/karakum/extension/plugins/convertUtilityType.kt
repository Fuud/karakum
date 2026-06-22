package io.github.sgrishchenko.karakum.extension.plugins

import io.github.sgrishchenko.karakum.extension.Context
import io.github.sgrishchenko.karakum.extension.Render
import io.github.sgrishchenko.karakum.extension.createPlugin
import io.github.sgrishchenko.karakum.extension.isBuiltin
import js.numbers.plus
import typescript.ExpressionWithTypeArguments
import typescript.Node
import typescript.NodeBuilderFlags
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

private suspend fun resolveAndRender(node: Node, context: Context, render: Render<Node>): String? {
    val checkCoverageService = context.lookupService(checkCoverageServiceKey)
    val typeScriptService = context.lookupService(typeScriptServiceKey)

    checkCoverageService?.deepCover(node)

    val resolvedType = typeScriptService?.resolveType(
        node.unsafeCast<typescript.TypeNode>(),
        context,
        NodeBuilderFlags.NoTruncation + NodeBuilderFlags.InTypeAlias,
    )

    if (resolvedType == null) return "Any /* ${typeScriptService?.printNode(node)} */"

    if (isConditionalTypeNode(resolvedType)) return "Any /* ${typeScriptService?.printNode(node)} */"

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

package io.github.sgrishchenko.karakum.extension.plugins

import io.github.sgrishchenko.karakum.extension.Context
import io.github.sgrishchenko.karakum.extension.ContextKey
import io.github.sgrishchenko.karakum.util.getSourceFileOrNull
import typescript.ModuleDeclaration
import typescript.Node
import typescript.TypeNode
import typescript.isMappedTypeNode
import typescript.isTypeLiteralNode

suspend fun registerTypeOverrideImports(
    node: Node,
    typeOverrides: Map<Int, TypeNode>,
    context: Context,
) {
    registerBaseTypeImports(node, typeOverrides.values.toList(), context)
}

suspend fun registerBaseTypeImports(
    node: Node,
    baseTypeNodes: List<TypeNode?>,
    context: Context,
) {
    val nameResolverService = context.lookupService(nameResolverServiceKey) ?: return
    val importInfoService = context.lookupService(importInfoServiceKey) ?: return
    val typeScriptService = context.lookupService(typeScriptServiceKey) ?: return

    val consumerSourceFileName = node.getSourceFileOrNull()?.fileName ?: return
    val consumerNamespace = typeScriptService.findClosestNamespace(node)
    val consumerPackage = computePackage(consumerSourceFileName, consumerNamespace, context)

    for (baseTypeNode in baseTypeNodes) {
        if (baseTypeNode == null || !isAnonymousTypeNode(baseTypeNode)) continue

        val baseSourceFileName = baseTypeNode.getSourceFileOrNull()?.fileName
            ?: typeScriptService.getSourceFile(baseTypeNode)?.fileName
            ?: continue
        val baseNamespace = typeScriptService.findClosestNamespace(baseTypeNode)
        val basePackage = computePackage(baseSourceFileName, baseNamespace, context)

        if (basePackage != consumerPackage) {
            val typeName = nameResolverService.resolveName(baseTypeNode, context)
            val fqn = resolveKotlinFqn(baseTypeNode, typeName, context)
            importInfoService.addDynamicImport(consumerSourceFileName, consumerNamespace, "import $fqn")
        }
    }
}

private fun isAnonymousTypeNode(node: Node): Boolean =
    isTypeLiteralNode(node) || isMappedTypeNode(node) || isInheritedTypeLiteral(node)

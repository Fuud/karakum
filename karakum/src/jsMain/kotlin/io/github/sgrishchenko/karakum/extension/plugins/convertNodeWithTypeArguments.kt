package io.github.sgrishchenko.karakum.extension.plugins

import io.github.sgrishchenko.karakum.extension.Context
import io.github.sgrishchenko.karakum.extension.Render
import io.github.sgrishchenko.karakum.extension.ifPresent
import io.github.sgrishchenko.karakum.extension.isBuiltin
import io.github.sgrishchenko.karakum.extension.plugins.typeScriptServiceKey
import io.github.sgrishchenko.karakum.util.getSourceFileOrNull
import js.numbers.contains
import typescript.*

suspend fun convertNodeWithTypeArguments(node: NodeWithTypeArguments, context: Context, render: Render<Node>): String {
    val explicitArgs = node.typeArguments?.asArray()
    val explicitCount = explicitArgs?.size ?: 0

    val defaultArgs = resolveDefaultTypeArguments(node, context, explicitCount, render)

    if (defaultArgs.isNotEmpty()) {
        val explicitRendered = explicitArgs
            ?.map { render(it) }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        val allRendered = explicitRendered + defaultArgs
        return "<${allRendered.joinToString(", ")}>"
    }

    val typeArguments = explicitArgs
        ?.map { render(it) }
        ?.filter { it.isNotEmpty() }
        ?.joinToString(separator = ", ")

    return ifPresent(typeArguments) { "<${it}>" }
}

private suspend fun resolveDefaultTypeArguments(
    node: NodeWithTypeArguments,
    context: Context,
    explicitCount: Int,
    render: Render<Node>,
): List<String> {
    val typeScriptService = context.lookupService(typeScriptServiceKey) ?: return emptyList()
    val typeChecker = typeScriptService.program.getTypeChecker()

    val identifier = extractIdentifier(node) ?: return emptyList()

    // Builtin types may have different type parameter counts in Kotlin vs TypeScript
    // (e.g., TS Iterable<T, TReturn, TNext> vs Kotlin JsIterable<T>).
    // Only resolve defaults for bare references (no explicit type args);
    // for partial references, skip defaults since the Kotlin mapping may differ.
    if (isBuiltin(identifier, context) && explicitCount > 0) return emptyList()

    var symbol = typeChecker.getSymbolAtLocation(identifier) ?: return emptyList()
    if (SymbolFlags.Alias in symbol.flags) {
        symbol = typeChecker.getAliasedSymbol(symbol)
    }

    val declarations = (symbol.declarations as Array<Declaration>?).orEmpty()
    val declaration = declarations.firstOrNull() ?: return emptyList()

    val typeParameters = when {
        isClassDeclaration(declaration) -> (declaration as ClassDeclaration).typeParameters?.asArray()
        isInterfaceDeclaration(declaration) -> (declaration as InterfaceDeclaration).typeParameters?.asArray()
        isTypeAliasDeclaration(declaration) -> (declaration as TypeAliasDeclaration).typeParameters?.asArray()
        else -> null
    }

    if (typeParameters == null || explicitCount >= typeParameters.size) return emptyList()

    registerDefaultTypeArgumentImports(node, typeParameters.asList(), explicitCount, context)

    return typeParameters.asList().drop(explicitCount)
        .mapNotNull { typeParam -> typeParam.default?.let { render(it) } }
        .filter { it.isNotEmpty() }
}

private fun extractIdentifier(node: NodeWithTypeArguments): Identifier? {
    return when {
        isTypeReferenceNode(node) -> {
            val typeName = (node as TypeReferenceNode).typeName
            if (isIdentifier(typeName)) typeName as Identifier else null
        }
        isExpressionWithTypeArguments(node) -> {
            val expression = (node as ExpressionWithTypeArguments).expression
            if (isIdentifier(expression)) expression as Identifier else null
        }
        else -> null
    }
}

@Suppress("UNCHECKED_AS_TO_EXTERNAL_INTERFACE")
private fun registerDefaultTypeArgumentImports(
    node: NodeWithTypeArguments,
    typeParameters: List<TypeParameterDeclaration>,
    explicitCount: Int,
    context: Context,
) {
    val typeScriptService = context.lookupService(typeScriptServiceKey) ?: return
    val importInfoService = context.lookupService(importInfoServiceKey) ?: return
    val typeChecker = typeScriptService.program.getTypeChecker()

    val sourceFileName = node.getSourceFileOrNull()?.fileName ?: return
    val namespace = typeScriptService.findClosestNamespace(node as Node)

    for (typeParam in typeParameters.drop(explicitCount)) {
        val defaultNode = typeParam.default ?: continue
        val defaultType = typeChecker.getTypeAtLocation(defaultNode)
        val gatheredImports = gatherImportsFromTypeReference(defaultType, sourceFileName, namespace, context)
        for (importStatement in gatheredImports) {
            importInfoService.addDynamicImport(sourceFileName, namespace, importStatement)
        }
    }
}

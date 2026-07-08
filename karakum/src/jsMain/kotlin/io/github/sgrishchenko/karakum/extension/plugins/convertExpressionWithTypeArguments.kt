package io.github.sgrishchenko.karakum.extension.plugins

import io.github.sgrishchenko.karakum.extension.createPlugin
import io.github.sgrishchenko.karakum.util.getSourceFileOrNull
import js.numbers.contains
import typescript.*

val convertExpressionWithTypeArguments = createPlugin plugin@{ node, context, render ->
    if (!isExpressionWithTypeArguments(node)) return@plugin null

    val checkCoverageService = context.lookupService(checkCoverageServiceKey)
    checkCoverageService?.cover(node)

    val expression = node.expression
    val renderedExpression = when {
        isIdentifier(expression) -> {
            registerImportForTypeNode(expression, context)
            val qualifier = resolveNamespaceQualifier(expression, context)
            if (qualifier != null) "$qualifier.${expression.text}" else render(expression)
        }
        isPropertyAccessExpression(expression) -> {
            val typeScriptService = context.lookupService(typeScriptServiceKey)
            val typeChecker = typeScriptService?.program?.getTypeChecker()
            var leftSymbol = typeChecker?.getSymbolAtLocation(expression.expression)

            if (typeChecker != null && leftSymbol != null && SymbolFlags.Alias in leftSymbol.flags) {
                leftSymbol = typeChecker.getAliasedSymbol(leftSymbol)
            }

            val isEnumReference = leftSymbol?.valueDeclaration?.let { isEnumDeclaration(it) } == true
                || leftSymbol?.declarations?.any { isEnumDeclaration(it) } == true

            val leftDecl = leftSymbol?.valueDeclaration ?: leftSymbol?.declarations?.firstOrNull()
            val leftDeclNode = leftDecl?.unsafeCast<Node>()
            val leftDeclSourceFileName = leftDeclNode?.getSourceFileOrNull()?.fileName
            val isSyntheticModuleNamespace = !isEnumReference
                && leftDeclNode != null
                && leftDeclSourceFileName != null
                && isInNodeModules(leftDeclSourceFileName)
                && isModuleMappedAsSinglePackage(leftDeclSourceFileName, context)
                && resolveExportedDeclarationName(leftDeclNode) == null

            when {
                isEnumReference -> {
                    checkCoverageService?.cover(expression)
                    checkCoverageService?.cover(expression.name)
                    render(expression.expression)
                }
                isSyntheticModuleNamespace -> {
                    val memberName = expression.name
                    if (isIdentifier(memberName)) {
                        registerImportForTypeNode(memberName, context)
                        val qualifier = resolveNamespaceQualifier(memberName, context)
                        if (qualifier != null) "$qualifier.${memberName.text}" else render(memberName)
                    } else {
                        render(expression.name)
                    }
                }
                else -> {
                    // Register import for the left part of PropertyAccessExpression
                    // (e.g. Vmoji in Vmoji.Receiver)
                    if (isIdentifier(expression.expression)) {
                        registerImportForTypeNode(expression.expression, context)
                    }
                    render(expression)
                }
            }
        }
        else -> render(expression)
    }

    renderedExpression + convertNodeWithTypeArguments(node, context, render)
}

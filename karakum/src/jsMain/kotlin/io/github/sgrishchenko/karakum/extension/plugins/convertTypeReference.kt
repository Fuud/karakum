package io.github.sgrishchenko.karakum.extension.plugins

import io.github.sgrishchenko.karakum.extension.createPlugin
import io.github.sgrishchenko.karakum.util.getSourceFileOrNull
import js.numbers.contains
import typescript.*

val convertTypeReference = createPlugin plugin@{ node, context, render ->
    if (!isTypeReferenceNode(node)) return@plugin null

    val checkCoverageService = context.lookupService(checkCoverageServiceKey)
    checkCoverageService?.cover(node)

    val typeName = node.typeName
    val renderedTypeName = when {
        isIdentifier(typeName) -> {
            registerImportForTypeNode(typeName, context)
            val qualifier = resolveNamespaceQualifier(typeName, context)
            if (qualifier != null) "$qualifier.${typeName.text}" else render(typeName)
        }
        isQualifiedName(typeName) -> {
            val typeScriptService = context.lookupService(typeScriptServiceKey)
            val typeChecker = typeScriptService?.program?.getTypeChecker()
            var leftSymbol = typeChecker?.getSymbolAtLocation(typeName.left)

            if (typeChecker != null && leftSymbol != null && SymbolFlags.Alias in leftSymbol.flags) {
                leftSymbol = typeChecker.getAliasedSymbol(leftSymbol)
            }

            val isEnumReference = leftSymbol?.valueDeclaration?.let { isEnumDeclaration(it) } == true
                || leftSymbol?.declarations?.any { isEnumDeclaration(it) } == true

            // When the left part of a QualifiedName resolves to a node_modules module
            // mapped with single package via importMapper, and the declaration is not
            // a named type (e.g. SourceFile from import type * as X), flatten the
            // QualifiedName. The module's types are generated as top-level declarations
            // in the mapped package (not nested in a namespace object), so
            // Vmoji.AnimojiVersion becomes just AnimojiVersion with a direct import.
            // This does NOT apply when the left part resolves to a real namespace
            // declaration (ModuleDeclaration) — in that case the namespace object IS
            // generated and Vmoji.AnimojiVersion is valid.
            val leftDecl = leftSymbol?.valueDeclaration ?: leftSymbol?.declarations?.firstOrNull()
            val leftDeclNode = leftDecl?.unsafeCast<Node>()
            val leftDeclSourceFileName = leftDeclNode?.getSourceFileOrNull()?.fileName
            val isSyntheticModuleNamespace = !isEnumReference
                && leftDeclNode != null
                && leftDeclSourceFileName != null
                && isInNodeModules(leftDeclSourceFileName)
                && isModuleMappedAsSinglePackage(leftDeclSourceFileName, context)
                && resolveExportedDeclarationName(leftDeclNode) == null

            // Register import for the left part only when it's NOT a synthetic module
            // namespace (in that case, we import the member directly instead).
            if (isIdentifier(typeName.left) && !isSyntheticModuleNamespace) {
                registerImportForTypeNode(typeName.left, context)
            }

            when {
                isEnumReference -> {
                    checkCoverageService?.cover(typeName)
                    checkCoverageService?.cover(typeName.right)
                    render(typeName.left)
                }
                isSyntheticModuleNamespace -> {
                    if (isIdentifier(typeName.right)) {
                        registerImportForTypeNode(typeName.right, context)
                        val qualifier = resolveNamespaceQualifier(typeName.right, context)
                        if (qualifier != null) "$qualifier.${typeName.right.text}" else render(typeName.right)
                    } else {
                        render(typeName.right)
                    }
                }
                else -> render(typeName)
            }
        }
        else -> render(typeName)
    }

    renderedTypeName + convertNodeWithTypeArguments(node, context, render)
}

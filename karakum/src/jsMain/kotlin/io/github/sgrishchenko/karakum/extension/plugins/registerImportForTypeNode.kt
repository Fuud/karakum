package io.github.sgrishchenko.karakum.extension.plugins

import io.github.sgrishchenko.karakum.extension.Context
import io.github.sgrishchenko.karakum.util.getSourceFileOrNull
import js.numbers.contains
import typescript.*

@Suppress("UNCHECKED_AS_TO_EXTERNAL_INTERFACE", "UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
fun registerImportForTypeNode(node: Node, context: Context) {
    val typeScriptService = context.lookupService(typeScriptServiceKey) ?: return
    val importInfoService = context.lookupService(importInfoServiceKey) ?: return
    val typeChecker = typeScriptService.program.getTypeChecker()

    var symbol = typeChecker.getSymbolAtLocation(node) ?: return
    if (SymbolFlags.Alias in symbol.flags) {
        symbol = typeChecker.getAliasedSymbol(symbol)
    }

    val declarations = (symbol.declarations as Array<Declaration>?).orEmpty()
    val consumerSourceFile = typeScriptService.getSourceFile(node)
    val consumerSourceFileName = consumerSourceFile?.fileName

    // Prefer declarations from the same source file as the consumer to avoid
    // cross-file symbol merging issues (e.g. same class declared in multiple .d.ts).
    val declaration = declarations.firstOrNull {
        it.unsafeCast<Node>().getSourceFileOrNull()?.fileName == consumerSourceFileName
    } ?: symbol.valueDeclaration
        ?: declarations.firstOrNull()
        ?: return
    val declNode = declaration.unsafeCast<Node>()

    val declSourceFileName = declNode.getSourceFileOrNull()?.fileName ?: return
    if (isBuiltinSourceFile(declSourceFileName)) return

    val identifierText = (node as? Identifier)?.text

    val typeName = resolveExportedDeclarationName(declNode)
        ?: if (isInNodeModules(declSourceFileName) && identifierText != null) {
            // For namespace imports (import type * as X from 'module'), the resolved
            // module symbol's declaration may be a SourceFile. Use the identifier text
            // as the type name so resolveNodeModulesImport can compute the import.
            identifierText
        } else null
        ?: return

    if (consumerSourceFileName == null) return
    val consumerNamespace = typeScriptService.findClosestNamespace(node)

    // When TypeScript merges declarations across files, the symbol may resolve
    // to a declaration in a different file even when the consumer's own file
    // declares the same type. Check if the consumer's source file has a local
    // declaration with the same name — if so, skip the import since the type
    // will be generated in the consumer's own package.
    if (consumerSourceFileName != declSourceFileName && consumerSourceFile != null) {
        val hasLocalDeclaration = (consumerSourceFile.statements as Array<Statement>).any { stmt ->
            val stmtName = when {
                isClassDeclaration(stmt) -> stmt.name?.text
                isInterfaceDeclaration(stmt) -> stmt.name?.text
                isTypeAliasDeclaration(stmt) -> stmt.name?.text
                isEnumDeclaration(stmt) -> stmt.name?.text
                else -> null
            }
            stmtName == typeName
        }
        if (hasLocalDeclaration) return
    }

    if (isInNodeModules(declSourceFileName)) {
        val resolved = resolveNodeModulesImport(declSourceFileName, typeName, context)
        if (resolved != null) {
            importInfoService.addDynamicImport(consumerSourceFileName, consumerNamespace, resolved)
        }
    } else {
        val consumerPackage = computePackage(consumerSourceFileName, consumerNamespace, context)
        val declNamespace = typeScriptService.findClosestNamespace(declNode)
        val declarationPackage = computePackage(declSourceFileName, declNamespace, context)
        if (declarationPackage != consumerPackage) {
            val fqn = resolveKotlinFqn(declNode, typeName, context)
            importInfoService.addDynamicImport(consumerSourceFileName, consumerNamespace, "import $fqn")
        }
    }
}

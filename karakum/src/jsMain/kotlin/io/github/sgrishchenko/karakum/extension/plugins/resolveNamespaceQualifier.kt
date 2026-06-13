package io.github.sgrishchenko.karakum.extension.plugins

import io.github.sgrishchenko.karakum.configuration.NamespaceStrategy
import io.github.sgrishchenko.karakum.configuration.`object`
import io.github.sgrishchenko.karakum.configuration.`package`
import io.github.sgrishchenko.karakum.extension.Context
import io.github.sgrishchenko.karakum.extension.findIdentifierUsage
import io.github.sgrishchenko.karakum.util.getParentOrNull
import io.github.sgrishchenko.karakum.util.getSourceFileOrNull
import js.numbers.contains
import typescript.*

fun resolveNamespaceQualifier(identifier: Node, context: Context): String? {
    val typeScriptService = context.lookupService(typeScriptServiceKey) ?: return null
    val namespaceInfoService = context.lookupService(namespaceInfoServiceKey) ?: return null
    val typeChecker = typeScriptService.program.getTypeChecker()

    var symbol = typeChecker.getSymbolAtLocation(identifier)

    if (symbol == null) {
        symbol = resolveSymbolFromVirtualNode(identifier, typeScriptService, typeChecker)
    }

    if (symbol == null) return null

    if (SymbolFlags.Alias in symbol.flags) {
        symbol = typeChecker.getAliasedSymbol(symbol)
    }

    val declaration = symbol.valueDeclaration
        ?: symbol.declarations?.firstOrNull()
        ?: return null

    if (isTypeParameterDeclaration(declaration)) return null

    val qualifiers = mutableListOf<String>()
    var current: Node? = typeScriptService.getParent(declaration)

    while (current != null) {
        if (isModuleDeclaration(current)) {
            val namespaceStrategy = namespaceInfoService.resolveNamespaceStrategy(current)
            if (namespaceStrategy == NamespaceStrategy.`object`) {
                val namespaceName = current.name
                val simpleName = when {
                    isIdentifier(namespaceName) -> namespaceName.text
                    isStringLiteral(namespaceName) -> namespaceName.text
                    else -> ""
                }
                if (simpleName.isNotEmpty()) {
                    qualifiers.add(0, simpleName)
                }
            } else if (namespaceStrategy == NamespaceStrategy.`package`) {
                break
            }
        }
        current = typeScriptService.getParent(current)
    }

    return if (qualifiers.isNotEmpty()) qualifiers.joinToString(".") else null
}

data class NamespaceQualifierResult(
    val qualifier: String?,
    val dynamicImportRegistered: Boolean,
)

fun resolveNamespaceQualifierAndRegisterImport(identifier: Node, context: Context): NamespaceQualifierResult {
    val typeScriptService = context.lookupService(typeScriptServiceKey) ?: return NamespaceQualifierResult(null, false)
    val typeChecker = typeScriptService.program.getTypeChecker()
    val importInfoService = context.lookupService(importInfoServiceKey)

    val qualifier = resolveNamespaceQualifier(identifier, context)

    // Resolve the symbol to find the declaration and register a demand-driven import
    var symbol = typeChecker.getSymbolAtLocation(identifier)
    if (symbol == null) {
        symbol = resolveSymbolFromVirtualNode(identifier, typeScriptService, typeChecker)
    }
    if (symbol == null) return NamespaceQualifierResult(qualifier, false)

    if (SymbolFlags.Alias in symbol.flags) {
        symbol = typeChecker.getAliasedSymbol(symbol)
    }

    val declaration = symbol.valueDeclaration ?: symbol.declarations?.firstOrNull()
    if (declaration != null && importInfoService != null) {
        // Skip builtin type declarations (TypeScript lib)
        if (isBuiltinDeclaration(declaration)) return NamespaceQualifierResult(qualifier, false)

        val declarationName = resolveExportedDeclarationName(declaration)
        if (declarationName != null) {
            val sourceFileName = typeScriptService.getSourceFile(identifier)?.fileName
            if (sourceFileName != null) {
                val namespace = typeScriptService.findClosestNamespace(identifier)

                // Only register import if the declaration is in a different package
                val consumerPackage = computePackage(sourceFileName, namespace, context)
                val declSourceFile = typeScriptService.getSourceFile(declaration)?.fileName ?: ""
                val declNamespace = typeScriptService.findClosestNamespace(declaration)
                val declarationPackage = computePackage(declSourceFile, declNamespace, context)

                if (consumerPackage != declarationPackage) {
                    val fqn = resolveKotlinFqn(declaration, declarationName, context)
                    importInfoService.addDynamicImport(sourceFileName, namespace, "import $fqn")
                    return NamespaceQualifierResult(qualifier, true)
                }
            }
        }
    }

    return NamespaceQualifierResult(qualifier, false)
}

private val builtinSourcePatterns = listOf(
    """^.*/typescript/lib/lib\.d\.ts$""".toRegex(),
    """^.*/typescript/lib/lib\.decorators\.d\.ts$""".toRegex(),
    """^.*/typescript/lib/lib\.dom\.d\.ts$""".toRegex(),
    """^.*/typescript/lib/lib\.es.+\.d\.ts$""".toRegex(),
    """^.*/typescript/lib/lib\.scripthost\.d\.ts$""".toRegex(),
    """^.*/typescript/lib/lib\.webworker\.d\.ts$""".toRegex(),
    """^.*/typescript/lib/lib\.webworker\.importscripts\.d\.ts$""".toRegex(),
)

private fun isBuiltinDeclaration(declaration: Node): Boolean {
    val sourceFileName = declaration.getSourceFileOrNull()?.fileName ?: return false
    return builtinSourcePatterns.any { it.matches(sourceFileName) }
}

private fun resolveExportedDeclarationName(declaration: Node): String? {
    return when {
        isInterfaceDeclaration(declaration) -> declaration.name?.text
        isClassDeclaration(declaration) -> declaration.name?.text
        isTypeAliasDeclaration(declaration) -> declaration.name?.text
        isEnumDeclaration(declaration) -> declaration.name?.text
        else -> null
    }
}

@Suppress("UNCHECKED_AS_TO_EXTERNAL_INTERFACE")
private fun resolveSymbolFromVirtualNode(
    identifier: Node,
    typeScriptService: TypeScriptService,
    typeChecker: TypeChecker,
): Symbol? {
    if (!isIdentifier(identifier)) return null

    // Try getTypeFromTypeNode on the parent TypeReferenceNode — the type checker
    // may resolve the type even for virtual nodes created by typeToTypeNode.
    val parent = identifier.getParentOrNull()
        ?: typeScriptService.getParent(identifier)

    if (parent != null && isTypeReferenceNode(parent)) {
        val type = try {
            typeChecker.getTypeFromTypeNode(parent.unsafeCast<TypeNode>())
        } catch (_: Throwable) {
            null
        }
        if (type != null && TypeFlags.Any !in type.flags) {
            val typeSymbol = type.symbol
            if (typeSymbol != null) return typeSymbol
        }
    }

    // Fallback: search source files for matching identifier in TypeReference context
    val identifierText = identifier.text
    val contextSourceFile = typeScriptService.getSourceFile(identifier)
    val sourceFiles = if (contextSourceFile != null) {
        arrayOf(contextSourceFile) + typeScriptService.program.getSourceFiles().filter { it.fileName != contextSourceFile.fileName }
    } else {
        typeScriptService.program.getSourceFiles()
    }

    for (sf in sourceFiles) {
        val originalNode = findIdentifierUsage(sf, identifierText)
        if (originalNode != null) {
            val originalSymbol = typeChecker.getSymbolAtLocation(originalNode)
            if (originalSymbol != null) return originalSymbol
        }
    }

    return null
}

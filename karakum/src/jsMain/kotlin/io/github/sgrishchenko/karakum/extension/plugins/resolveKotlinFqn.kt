package io.github.sgrishchenko.karakum.extension.plugins

import io.github.sgrishchenko.karakum.configuration.NamespaceStrategy
import io.github.sgrishchenko.karakum.configuration.`object`
import io.github.sgrishchenko.karakum.configuration.`package`
import io.github.sgrishchenko.karakum.extension.Context
import io.github.sgrishchenko.karakum.structure.namespace.createNamespaceInfoItem
import io.github.sgrishchenko.karakum.structure.`package`.applyPackageNameMapper
import io.github.sgrishchenko.karakum.structure.`package`.createPackageName
import io.github.sgrishchenko.karakum.structure.sourceFile.createSourceFileInfoItem
import io.github.sgrishchenko.karakum.util.escapeIdentifier
import js.array.ReadonlyArray
import typescript.ModuleDeclaration
import typescript.Node
import typescript.isIdentifier
import typescript.isModuleDeclaration
import typescript.isStringLiteral

fun computePackage(
    sourceFileName: String,
    namespace: ModuleDeclaration?,
    context: Context,
): String {
    val configurationService = context.requireService(configurationServiceKey)
    val importInfoService = context.requireService(importInfoServiceKey)
    val namespaceInfoService = context.requireService(namespaceInfoServiceKey)
    val configuration = configurationService.configuration

    val imports = importInfoService.resolveImports(sourceFileName, namespace)

    val packageChunks: ReadonlyArray<String>
    val fileName: String

    if (namespace != null && namespaceInfoService.resolveNamespaceStrategy(namespace) == NamespaceStrategy.`package`) {
        val item = createNamespaceInfoItem(namespace, sourceFileName, imports, configuration)
        packageChunks = item.`package`
        fileName = item.fileName
    } else {
        val item = createSourceFileInfoItem(sourceFileName, imports, configuration)
        packageChunks = item.`package`
        fileName = item.fileName
    }

    val mappingResult = applyPackageNameMapper(packageChunks, fileName, configuration)
    return createPackageName(mappingResult.`package`)
}

private fun resolveObjectQualifiers(
    declaration: Node,
    context: Context,
): List<String> {
    val typeScriptService = context.requireService(typeScriptServiceKey)
    val namespaceInfoService = context.requireService(namespaceInfoServiceKey)

    val qualifiers = mutableListOf<String>()

    var current: Node? = typeScriptService.getParent(declaration)

    while (current != null) {
        if (isModuleDeclaration(current)) {
            val namespaceStrategy = namespaceInfoService.resolveNamespaceStrategy(current)

            if (namespaceStrategy == NamespaceStrategy.`object`) {
                val nameNode = current.name
                val simpleName = when {
                    isStringLiteral(nameNode) -> nameNode.text
                    isIdentifier(nameNode) -> nameNode.text
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

    return qualifiers
}

fun resolveKotlinFqn(
    declaration: Node,
    declarationName: String,
    context: Context,
): String {
    val typeScriptService = context.requireService(typeScriptServiceKey)
    val qualifiers = resolveObjectQualifiers(declaration, context)

    val declarationSourceFile = typeScriptService.getSourceFile(declaration)?.fileName ?: ""
    val declarationNamespace = typeScriptService.findClosestNamespace(declaration)
    val declarationPackage = computePackage(declarationSourceFile, declarationNamespace, context)

    val qualifiedName = (qualifiers + declarationName).joinToString(".") { escapeIdentifier(it) }
    return if (declarationPackage.isNotEmpty()) {
        "$declarationPackage.$qualifiedName"
    } else {
        qualifiedName
    }
}

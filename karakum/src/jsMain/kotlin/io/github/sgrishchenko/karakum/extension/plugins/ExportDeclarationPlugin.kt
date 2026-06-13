package io.github.sgrishchenko.karakum.extension.plugins

import io.github.sgrishchenko.karakum.configuration.NamespaceStrategy
import io.github.sgrishchenko.karakum.configuration.`object`
import io.github.sgrishchenko.karakum.configuration.`package`
import io.github.sgrishchenko.karakum.extension.Context
import io.github.sgrishchenko.karakum.extension.GeneratedFile
import io.github.sgrishchenko.karakum.extension.Plugin
import io.github.sgrishchenko.karakum.extension.Render
import io.github.sgrishchenko.karakum.extension.ifPresent
import io.github.sgrishchenko.karakum.structure.derived.DerivedDeclaration
import io.github.sgrishchenko.karakum.structure.derived.generateDerivedDeclarations
import io.github.sgrishchenko.karakum.structure.namespace.createNamespaceInfoItem
import io.github.sgrishchenko.karakum.structure.`package`.applyPackageNameMapper
import io.github.sgrishchenko.karakum.structure.`package`.createPackageName
import io.github.sgrishchenko.karakum.structure.sourceFile.createSourceFileInfoItem
import io.github.sgrishchenko.karakum.util.escapeIdentifier
import io.github.sgrishchenko.karakum.util.getSourceFileOrNull
import js.array.ReadonlyArray
import js.array.component1
import js.array.component2
import js.numbers.contains
import typescript.*

class ExportDeclarationPlugin : Plugin {
    private val generated = mutableListOf<DerivedDeclaration>()
    private val seenNames = mutableSetOf<String>()

    override suspend fun setup(context: Context) = Unit

    override suspend fun traverse(node: Node, context: Context) {
        if (!isExportDeclaration(node)) return

        val checkCoverageService = context.lookupService(checkCoverageServiceKey)
        checkCoverageService?.cover(node)

        val exportClause = node.exportClause
        if (exportClause != null) {
            checkCoverageService?.cover(exportClause)
            if (isNamedExports(exportClause)) {
                exportClause.elements.asArray().forEach { element ->
                    checkCoverageService?.cover(element)
                    checkCoverageService?.cover(element.name)
                }
            }
        }

        val typeScriptService = context.requireService(typeScriptServiceKey)
        val namespaceInfoService = context.requireService(namespaceInfoServiceKey)

        val typeChecker = typeScriptService.program.getTypeChecker()

        val moduleSpecifier = node.moduleSpecifier
        if (moduleSpecifier == null || !isStringLiteral(moduleSpecifier)) return

        if (exportClause != null && !isNamedExports(exportClause)) return

        val sourceFileName = node.getSourceFileOrNull()?.fileName ?: return
        val namespace = typeScriptService.findClosestNamespace(node)

        val moduleSymbol = typeChecker.getSymbolAtLocation(moduleSpecifier) ?: return

        val nameSymbolPairs = mutableListOf<Pair<String, Symbol>>()

        if (exportClause == null) {
            // `export * from "module"` — get all exports of the module
            val exports = typeChecker.getExportsOfModule(moduleSymbol)
            if (exports != null) {
                for (i in 0 until exports.length) {
                    val symbol = exports[i] ?: continue
                    val aliasedSymbol = if (SymbolFlags.Alias in symbol.flags) {
                        typeChecker.getAliasedSymbol(symbol)
                    } else {
                        symbol
                    }
                    val name = symbol.escapedName?.toString() ?: continue
                    nameSymbolPairs.add(Pair(name, aliasedSymbol))
                }
            }
        } else {
            // `export { X, Y as Z } from "module"`
            exportClause.elements.asArray().forEach { element ->
                val localSymbol = typeChecker.getSymbolAtLocation(element.name)
                    ?: return@forEach

                val aliasedSymbol = if (SymbolFlags.Alias in localSymbol.flags) {
                    typeChecker.getAliasedSymbol(localSymbol)
                } else {
                    localSymbol
                }

                val exportedName = element.name.asDynamic().text.unsafeCast<String>()
                nameSymbolPairs += Pair(exportedName, aliasedSymbol)
            }
        }

        for ((exportedName, aliasedSymbol) in nameSymbolPairs) {
            if (exportedName in seenNames) continue
            if (!isTypeLevelSymbol(aliasedSymbol)) continue

            val declaration = aliasedSymbol.valueDeclaration
                ?: aliasedSymbol.declarations?.firstOrNull()
                ?: continue

            val declarationName = resolveDeclarationName(declaration) ?: continue
            val qualifiers = resolveObjectQualifiers(declaration, context)

            val targetPackage = computePackage(sourceFileName, namespace, context)
            val declarationSourceFile = declaration.getSourceFileOrNull()?.fileName ?: continue
            val declarationNamespace = typeScriptService.findClosestNamespace(declaration)
            val declarationPackage = computePackage(declarationSourceFile, declarationNamespace, context)

            if (targetPackage == declarationPackage && qualifiers.isEmpty()) continue

            val qualifiedName = (qualifiers + declarationName).joinToString(".") { escapeIdentifier(it) }
            val fqn = if (declarationPackage.isNotEmpty()) {
                "$declarationPackage.$qualifiedName"
            } else {
                qualifiedName
            }

            seenNames += exportedName

            val typeParamNames = resolveTypeParameterNames(declaration)
            val body = "typealias ${escapeIdentifier(exportedName)}${ifPresent(typeParamNames) { "<$it>" }} = $fqn${ifPresent(typeParamNames) { "<$it>" }}"

            generated += DerivedDeclaration(
                sourceFileName = sourceFileName,
                namespace = namespace,
                fileName = "${exportedName}.kt",
                body = body,
            )
        }
    }

    override suspend fun render(node: Node, context: Context, next: Render<Node>): String? {
        if (isExportDeclaration(node)) return ""
        return null
    }

    override suspend fun generate(context: Context, render: Render<Node>): ReadonlyArray<GeneratedFile> {
        return generateDerivedDeclarations(generated.toTypedArray(), context)
    }
}

private fun computePackage(
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

private fun isTypeLevelSymbol(symbol: Symbol): Boolean {
    val declaration = symbol.valueDeclaration ?: symbol.declarations?.firstOrNull() ?: return false
    return isInterfaceDeclaration(declaration)
        || isClassDeclaration(declaration)
        || isTypeAliasDeclaration(declaration)
        || isEnumDeclaration(declaration)
}

private fun resolveDeclarationName(declaration: Node): String? {
    return when {
        isInterfaceDeclaration(declaration) -> declaration.name?.text
        isClassDeclaration(declaration) -> declaration.name?.text
        isTypeAliasDeclaration(declaration) -> declaration.name?.text
        isEnumDeclaration(declaration) -> declaration.name?.text
        else -> null
    }
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

private fun resolveTypeParameterNames(declaration: Node): String? {
    val typeParams = when {
        isClassDeclaration(declaration) -> declaration.typeParameters?.asArray()
        isInterfaceDeclaration(declaration) -> declaration.typeParameters?.asArray()
        isTypeAliasDeclaration(declaration) -> declaration.typeParameters?.asArray()
        else -> null
    }

    return typeParams
        ?.map { escapeIdentifier(it.name.text) }
        ?.joinToString(", ")
        ?.takeIf { it.isNotEmpty() }
}

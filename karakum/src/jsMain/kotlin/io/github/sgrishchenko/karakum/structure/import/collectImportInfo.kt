package io.github.sgrishchenko.karakum.structure.import

import io.github.sgrishchenko.karakum.configuration.Configuration
import io.github.sgrishchenko.karakum.structure.removePrefix
import io.github.sgrishchenko.karakum.util.recordOrNull
import io.github.sgrishchenko.karakum.util.singleOrNull
import io.github.sgrishchenko.karakum.util.toPosix
import io.github.sgrishchenko.karakum.util.traverse
import io.github.sgrishchenko.karakum.util.traverseSync
import js.array.ReadonlyArray
import js.array.component1
import js.array.component2
import js.objects.Object
import js.objects.recordOf
import node.path.path
import typescript.*

typealias ImportInfo = Map<Declaration, ReadonlyArray<String>>

private fun handleImportHierarchy(importInfo: ImportInfo): ImportInfo {
    val result = mutableMapOf<Declaration, ReadonlyArray<String>>()

    for ((declaration) in importInfo) {
        var parent: Node? = declaration
        var collectedImports = emptyArray<String>()

        while (parent != null) {
            @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
            val parentImports = importInfo[parent as Declaration] ?: emptyArray()
            collectedImports = parentImports + collectedImports
            parent = parent.parent
        }

        result[declaration] = collectedImports
    }

    return result
}

private fun resolveKotlinImportName(importName: String, importAlias: String): String? {
    return when (importName) {
        "default" -> importAlias
        "*" -> null
        else -> importName
    }
}

private fun resolveImportModuleSpecifier(
    rawSpecifier: String,
    sourceFileName: String,
    inputRoots: List<String>,
): String {
    if (!rawSpecifier.startsWith("./") && !rawSpecifier.startsWith("../")) {
        return rawSpecifier
    }

    val sourceDir = path.dirname(sourceFileName)
    val absoluteResolved = path.resolve(sourceDir, rawSpecifier)
    val normalizedAbsolute = toPosix(absoluteResolved)

    val resolved = removePrefix(normalizedAbsolute, inputRoots)
    val stripped = if (resolved.startsWith("/")) resolved.removePrefix("/") else resolved

    return "./$stripped"
}

fun collectImportInfo(
    sourceFiles: ReadonlyArray<SourceFile>,
    configuration: Configuration,
): ImportInfo {
    val importMapper = configuration.importMapper
    val result = mutableMapOf<Declaration, ReadonlyArray<String>>()
    val declarations = mutableSetOf<Declaration>()

    sourceFiles.forEach { sourceFile ->
        traverseSync(sourceFile) { node ->
            if (isSourceFile(node)) declarations += node
            if (isModuleDeclaration(node)) declarations += node

            if (isImportDeclaration(node)) {
                val moduleSpecifier = node.moduleSpecifier
                val importClause = node.importClause

                if (!isStringLiteral(moduleSpecifier)) return@traverseSync
                if (importClause == null) return@traverseSync

                val parent = node.parent

                val declaration = if (isSourceFile(parent)) {
                    parent
                } else if (isModuleBlock(parent)) {
                    parent.parent
                } else {
                    return@traverseSync
                }

                val rawSpecifier = moduleSpecifier.text
                val moduleName = resolveImportModuleSpecifier(rawSpecifier, sourceFile.fileName, configuration.inputRoots)
                val imports = result[declaration]?.toMutableList() ?: mutableListOf()

                val importNames = recordOf<String, /* alias */ String>()

                val importClauseName = importClause.name

                if (importClauseName != null) {
                    importNames["default"] = importClauseName.text
                }

                val importClauseNamedBindings = importClause.namedBindings

                if (importClauseNamedBindings != null) {
                    if (isNamespaceImport(importClauseNamedBindings)) {
                        importNames["*"] = importClauseNamedBindings.name.text
                    } else if (isNamedImports(importClauseNamedBindings)) {
                        importClauseNamedBindings.elements.asArray().forEach { element ->
                            val propertyName = element.propertyName

                            if (propertyName != null) {
                                if (isStringLiteral(propertyName)) {
                                    importNames[propertyName.text] = element.name.text
                                } else if (isIdentifier(propertyName)) {
                                    importNames[propertyName.text] = element.name.text
                                }
                            } else {
                                importNames[element.name.text] = element.name.text
                            }
                        }
                    }
                }

                val unhandledImportNames = Object.keys(importNames).toMutableSet()

                for ((moduleNamePattern, packageInfo) in importMapper) {
                    val moduleNameRegexp = moduleNamePattern.toRegex()

                    if (moduleNameRegexp.containsMatchIn(moduleName)) {
                        val singlePackageName = packageInfo.singleOrNull()
                        val packageRecord = packageInfo.recordOrNull()

                        if (singlePackageName != null) {
                            for ((importName, importAlias) in Object.entries(importNames)) {
                                val effectiveImportName = resolveKotlinImportName(importName, importAlias)
                                if (effectiveImportName != null) {
                                    imports += if (effectiveImportName == importAlias) {
                                        "import ${singlePackageName}.${effectiveImportName}"
                                    } else {
                                        "import ${singlePackageName}.${effectiveImportName} as $importAlias"
                                    }
                                }
                                unhandledImportNames -= importName
                            }
                        } else if (packageRecord != null) {
                            for ((importNamePattern, packageName) in Object.entries(packageRecord)) {
                                val importNameRegexp = importNamePattern.toRegex()

                                for ((importName, importAlias) in Object.entries(importNames)) {
                                    if (importNameRegexp.containsMatchIn(importName) && importName in unhandledImportNames) {
                                        if (packageName.endsWith(".")) {
                                            val effectiveImportName = resolveKotlinImportName(importName, importAlias)
                                            if (effectiveImportName != null) {
                                                imports += if (effectiveImportName == importAlias) {
                                                    "import ${packageName}${effectiveImportName}"
                                                } else {
                                                    "import ${packageName}${effectiveImportName} as $importAlias"
                                                }
                                            }
                                        } else if (packageName.isNotEmpty()) {
                                            imports += if (importName == importAlias || " as " in packageName) {
                                                "import $packageName"
                                            } else {
                                                "import $packageName as $importAlias"
                                            }
                                        }

                                        unhandledImportNames -= importName
                                    }
                                }
                            }
                        }
                    }
                }

                for (importName in unhandledImportNames) {
                    val importAlias = importNames[importName]

                    imports += if (importName == importAlias) {
                        "// unhandled import: $importName from \"${moduleName}\""
                    } else {
                        "// unhandled import: $importName as $importAlias from \"${moduleName}\""
                    }
                }

                result[declaration] = imports.toTypedArray()
            }
        }
    }

    for (declaration in declarations) {
        if (declaration !in result) {
            result[declaration] = emptyArray()
        }
    }

    return handleImportHierarchy(result)
}

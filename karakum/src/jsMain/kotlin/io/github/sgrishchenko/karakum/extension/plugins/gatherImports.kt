package io.github.sgrishchenko.karakum.extension.plugins

import io.github.sgrishchenko.karakum.configuration.Configuration
import io.github.sgrishchenko.karakum.extension.Context
import io.github.sgrishchenko.karakum.structure.`package`.applyPackageNameMapper
import io.github.sgrishchenko.karakum.structure.`package`.createPackageName
import io.github.sgrishchenko.karakum.structure.`package`.dirNameToPackage
import io.github.sgrishchenko.karakum.util.getSourceFileOrNull
import io.github.sgrishchenko.karakum.util.recordOrNull
import io.github.sgrishchenko.karakum.util.singleOrNull
import js.array.component1
import js.array.component2
import js.objects.Object
import js.numbers.contains
import typescript.*

private val builtinSourcePatterns = listOf(
    """^.*/typescript/lib/lib\.d\.ts$""".toRegex(),
    """^.*/typescript/lib/lib\.decorators\.d\.ts$""".toRegex(),
    """^.*/typescript/lib/lib\.dom\.d\.ts$""".toRegex(),
    """^.*/typescript/lib/lib\.es.+\.d\.ts$""".toRegex(),
    """^.*/typescript/lib/lib\.scripthost\.d\.ts$""".toRegex(),
    """^.*/typescript/lib/lib\.webworker\.d\.ts$""".toRegex(),
    """^.*/typescript/lib/lib\.webworker\.importscripts\.d\.ts$""".toRegex(),
)

internal fun isBuiltinSourceFile(fileName: String): Boolean {
    return builtinSourcePatterns.any { it.matches(fileName) }
}

internal fun isInNodeModules(fileName: String): Boolean {
    return "/node_modules/" in fileName || "\\node_modules\\" in fileName
}

// Captures the npm package name from a node_modules path.
// First alternative matches scoped packages: @scope/name
// Second alternative matches unscoped packages: name
// Subdirectories after the package name are NOT captured.
private val nodeModulesPattern = """[/\\]node_modules[/\\](@[^/\\]+[/\\][^/\\]+|[^/\\]+)[/\\]""".toRegex()

internal fun extractNodeModulesName(fileName: String): String? {
    val match = nodeModulesPattern.find(fileName) ?: return null
    val name = match.groupValues[1]
    return name.replace("\\", "/")
}

private fun extractNodeModulesSubDir(fileName: String): String {
    val idx = fileName.indexOf("/node_modules/")
    val idx2 = if (idx >= 0) idx else fileName.indexOf("\\node_modules\\")
    if (idx2 < 0) return ""

    val afterNodeModules = fileName.substring(idx2 + "/node_modules/".length)
    val segments = afterNodeModules.split("[/\\\\]".toRegex())

    val packageSegments = if (segments.isNotEmpty() && segments[0].startsWith("@")) 2 else 1
    if (segments.size <= packageSegments) return ""

    val subDirSegments = segments.drop(packageSegments).dropLast(1)
    if (subDirSegments.isEmpty()) return ""

    return subDirSegments.joinToString("/")
}

internal fun computePackageForNodeModulesImport(
    declSourceFileName: String,
    basePackageName: String,
    configuration: Configuration,
): String {
    val subDir = extractNodeModulesSubDir(declSourceFileName)
    if (subDir.isEmpty()) return basePackageName

    val baseChunks = basePackageName.split(".")
    val subDirChunks = dirNameToPackage(subDir)
    val packageChunks = (baseChunks + subDirChunks).toTypedArray()

    val mappingResult = applyPackageNameMapper(packageChunks, "module.kt", configuration)
    return createPackageName(mappingResult.`package`)
}

@Suppress("UNCHECKED_AS_TO_EXTERNAL_INTERFACE", "UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
internal fun resolveExportedDeclarationName(declaration: Node): String? {
    return when {
        isInterfaceDeclaration(declaration) -> declaration.name?.text
        isClassDeclaration(declaration) -> declaration.name?.text
        isTypeAliasDeclaration(declaration) -> declaration.name?.text
        isEnumDeclaration(declaration) -> declaration.name?.text
        isModuleDeclaration(declaration) -> (declaration.name as? Identifier)?.text
        else -> null
    }
}

internal fun resolveNodeModulesImport(
    declSourceFileName: String,
    typeName: String,
    context: Context,
): String? {
    val configurationService = context.lookupService(configurationServiceKey) ?: return null
    val configuration = configurationService.configuration
    val importMapper = configuration.importMapper

    val moduleName = extractNodeModulesName(declSourceFileName) ?: return null

    for ((moduleNamePattern, packageInfo) in importMapper) {
        val moduleNameRegexp = moduleNamePattern.toRegex()

        if (moduleNameRegexp.containsMatchIn(moduleName)) {
            val singlePackageName = packageInfo.singleOrNull()

            if (singlePackageName != null) {
                val autoPackage = computePackageForNodeModulesImport(declSourceFileName, singlePackageName, configuration)
                return "import ${autoPackage}.${typeName}"
            }

            val packageRecord = packageInfo.recordOrNull()

            if (packageRecord != null) {
                for ((importNamePattern, packageName) in Object.entries(packageRecord)) {
                    val importNameRegexp = importNamePattern.toRegex()

                    if (importNameRegexp.containsMatchIn(typeName)) {
                        if (packageName.endsWith(".")) {
                            return "import ${packageName}${typeName}"
                        } else if (packageName.isNotEmpty()) {
                            return if (" as " in packageName) {
                                "import $packageName"
                            } else {
                                "import $packageName as $typeName"
                            }
                        }
                    }
                }
            }
        }
    }

    return null
}

internal fun isModuleMappedAsSinglePackage(declSourceFileName: String, context: Context): Boolean {
    val configurationService = context.lookupService(configurationServiceKey) ?: return false
    val configuration = configurationService.configuration
    val importMapper = configuration.importMapper

    val moduleName = extractNodeModulesName(declSourceFileName) ?: return false

    for ((moduleNamePattern, packageInfo) in importMapper) {
        val moduleNameRegexp = moduleNamePattern.toRegex()
        if (moduleNameRegexp.containsMatchIn(moduleName)) {
            return packageInfo.singleOrNull() != null
        }
    }

    return false
}

private val primitiveFlags = setOf(
    TypeFlags.String,
    TypeFlags.Number,
    TypeFlags.Boolean,
    TypeFlags.Any,
    TypeFlags.Void,
    TypeFlags.Null,
    TypeFlags.Undefined,
    TypeFlags.Never,
    TypeFlags.BigInt,
    TypeFlags.ESSymbol,
)

@Suppress("UNCHECKED_AS_TO_EXTERNAL_INTERFACE", "UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
private fun walkTypeAndGatherImports(
    type: Type,
    typeChecker: TypeChecker,
    consumerPackage: String,
    context: Context,
    imports: MutableList<String>,
    visited: MutableSet<Type>,
) {
    if (type in visited) return
    visited.add(type)

    // Check aliasSymbol before primitive flags — type aliases over primitive types
    // (e.g. type Status = "active" | "inactive") have primitive flags but still
    // need import registration via their aliasSymbol.
    @Suppress("UNCHECKED_AS_TO_EXTERNAL_INTERFACE")
    val aliasSymbol = type.asDynamic().aliasSymbol
    if (aliasSymbol != null) {
        var resolvedAlias = aliasSymbol.unsafeCast<Symbol>()
        if (SymbolFlags.Alias in resolvedAlias.flags) {
            resolvedAlias = typeChecker.getAliasedSymbol(resolvedAlias)
        }

        val aliasDeclaration = resolvedAlias.valueDeclaration
            ?: resolvedAlias.declarations?.firstOrNull()

        if (aliasDeclaration != null) {
            val aliasDeclNode = aliasDeclaration.unsafeCast<Node>()
            val typeName = resolveExportedDeclarationName(aliasDeclNode)
            if (typeName != null) {
                val declSourceFileName = aliasDeclNode.getSourceFileOrNull()?.fileName
                if (declSourceFileName != null && !isBuiltinSourceFile(declSourceFileName)) {
                    if (isInNodeModules(declSourceFileName)) {
                        val typeScriptService = context.lookupService(typeScriptServiceKey)!!
                        val isNamespaceMember = typeScriptService.findClosestNamespace(aliasDeclNode) != null
                        if (!isNamespaceMember) {
                            val resolved = resolveNodeModulesImport(declSourceFileName, typeName, context)
                            if (resolved != null) {
                                imports.add(resolved)
                            }
                        }
                    } else {
                        val typeScriptService = context.lookupService(typeScriptServiceKey)!!
                        val declNamespace = typeScriptService.findClosestNamespace(aliasDeclNode)
                        val declarationPackage = computePackage(declSourceFileName, declNamespace, context)

                        if (declarationPackage != consumerPackage) {
                            val isObjectMember = resolveObjectQualifiers(aliasDeclNode, context).isNotEmpty()
                            if (!isObjectMember) {
                                val fqn = resolveKotlinFqn(aliasDeclNode, typeName, context)
                                imports.add("import $fqn")
                            }
                        }
                    }
                }
            }
        }

        return
    }

    if (primitiveFlags.any { it in type.flags }) return

    if (TypeFlags.Union in type.flags) {
        val unionType = type.unsafeCast<UnionType>()
        unionType.types.asList().forEach { walkTypeAndGatherImports(it, typeChecker, consumerPackage, context, imports, visited) }
        return
    }

    if (TypeFlags.Intersection in type.flags) {
        val intersectionType = type.unsafeCast<IntersectionType>()
        intersectionType.types.asList().forEach { walkTypeAndGatherImports(it, typeChecker, consumerPackage, context, imports, visited) }
        return
    }

    // Walk type arguments of instantiated generic types (e.g. IEffect<IEffectDrawParams, OffscreenRenderingContext>)
    val objectType = type.asDynamic().objectFlags
    if (objectType != null && ObjectFlags.Reference in objectType.unsafeCast<ObjectFlags>()) {
        val typeRef = type.unsafeCast<TypeReference>()
        val typeArgs = typeRef.typeArguments
        if (typeArgs != null) {
            typeArgs.asList().forEach { walkTypeAndGatherImports(it, typeChecker, consumerPackage, context, imports, visited) }
        }
    }

    // Walk properties of anonymous object types (e.g. type literals from
    // utility type expansion like Parameters<typeof X>[0]).
    // The type itself is not importable (resolveExportedDeclarationName
    // returns null for TypeLiteralNode), but its property types may
    // reference importable types from other packages.
    if (objectType != null && ObjectFlags.Anonymous in objectType.unsafeCast<ObjectFlags>()) {
        val symbol = type.symbol
        val hasNamedDeclaration = symbol
            ?.let { it.valueDeclaration ?: it.declarations?.firstOrNull() }
            ?.let { resolveExportedDeclarationName(it.unsafeCast<Node>()) } != null

        if (!hasNamedDeclaration) {
            type.getPropertiesAsSymbols().forEach { prop ->
                val propType = typeChecker.getPropertyType(type, prop)
                walkTypeAndGatherImports(propType, typeChecker, consumerPackage, context, imports, visited)
            }
        }
    }

    val symbol = type.symbol
    if (symbol == null) return

    var resolvedSymbol = symbol
    if (SymbolFlags.Alias in resolvedSymbol.flags) {
        resolvedSymbol = typeChecker.getAliasedSymbol(resolvedSymbol)
    }

    val declaration = resolvedSymbol.valueDeclaration
        ?: resolvedSymbol.declarations?.firstOrNull()

    if (declaration != null) {
        val declNode = declaration.unsafeCast<Node>()

        val typeName = resolveExportedDeclarationName(declNode) ?: return

        val declSourceFileName = declNode.getSourceFileOrNull()?.fileName ?: return

        if (isBuiltinSourceFile(declSourceFileName)) return

        val typeScriptService = context.lookupService(typeScriptServiceKey)!!

        if (isInNodeModules(declSourceFileName)) {
            // Skip types that are members of a namespace from node_modules.
            // They are accessed via the namespace object (e.g. Vmoji.AnimojiVersion),
            // which is imported as a whole via registerImportForTypeNode for QualifiedName.
            val isNamespaceMember = typeScriptService.findClosestNamespace(declNode) != null
            if (!isNamespaceMember) {
                val resolved = resolveNodeModulesImport(declSourceFileName, typeName, context)
                if (resolved != null) {
                    imports.add(resolved)
                }
            }
        } else {
            val declNamespace = typeScriptService.findClosestNamespace(declNode)
            val declarationPackage = computePackage(declSourceFileName, declNamespace, context)

            if (declarationPackage != consumerPackage) {
                val isObjectMember = resolveObjectQualifiers(declNode, context).isNotEmpty()
                if (!isObjectMember) {
                    val fqn = resolveKotlinFqn(declNode, typeName, context)
                    imports.add("import $fqn")
                }
            }
        }
    }
}

@Suppress("UNCHECKED_AS_TO_EXTERNAL_INTERFACE", "UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
private fun Type.getPropertiesAsSymbols(): Array<Symbol> {
    val props = this.asDynamic().getProperties()
    if (props != null) return props.unsafeCast<Array<Symbol>>()

    val apparentProps = this.asDynamic().getApparentProperties()
    return if (apparentProps != null) apparentProps.unsafeCast<Array<Symbol>>() else emptyArray()
}

@Suppress("UNCHECKED_AS_TO_EXTERNAL_INTERFACE", "UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
private fun TypeChecker.getPropertyType(container: Type, symbol: Symbol): Type {
    val propName = symbol.name
    val contextualType = this.asDynamic().getTypeOfPropertyOfType(container, propName)
    if (contextualType != null) return contextualType.unsafeCast<Type>()

    val decl = symbol.valueDeclaration ?: symbol.declarations?.firstOrNull()
    return if (decl != null) {
        this.getTypeAtLocation(decl)
    } else {
        this.asDynamic().getDeclaredTypeOfSymbol(symbol).unsafeCast<Type>()
    }
}

@Suppress("UNCHECKED_AS_TO_EXTERNAL_INTERFACE", "UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
fun gatherImportsFromTypeReference(
    type: Type,
    consumerSourceFileName: String,
    consumerNamespace: ModuleDeclaration?,
    context: Context,
    walkProperties: Boolean = false,
): List<String> {
    val typeScriptService = context.lookupService(typeScriptServiceKey) ?: return emptyList()
    val typeChecker = typeScriptService.program.getTypeChecker()

    val consumerPackage = computePackage(consumerSourceFileName, consumerNamespace, context)
    val imports = mutableListOf<String>()
    val visited = mutableSetOf<Type>()

    walkTypeAndGatherImports(type, typeChecker, consumerPackage, context, imports, visited)

    // Walk properties only when explicitly requested (e.g. for expanded utility types
    // like Partial<T> where constituent types are only discoverable through the Type object).
    // Skip namespace types — their properties are already accessible through the
    // namespace import (e.g. Vmoji.AnimojiVersion), and walking them causes dead imports
    // from node_modules subdirectories.
    if (walkProperties) {
        val isNamespaceType = type.symbol != null
                && type.symbol.declarations?.any { isModuleDeclaration(it) } == true
        if (!isNamespaceType) {
            type.getPropertiesAsSymbols().forEach { prop ->
                val propType = typeChecker.getPropertyType(type, prop)
                walkTypeAndGatherImports(propType, typeChecker, consumerPackage, context, imports, visited)
            }
        }
    }

    return imports.distinct()
}

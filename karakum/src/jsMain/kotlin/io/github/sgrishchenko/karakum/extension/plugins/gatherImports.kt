package io.github.sgrishchenko.karakum.extension.plugins

import io.github.sgrishchenko.karakum.configuration.Configuration
import io.github.sgrishchenko.karakum.extension.Context
import io.github.sgrishchenko.karakum.structure.module.moduleNameToPackage
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

private fun isBuiltinSourceFile(fileName: String): Boolean {
    return builtinSourcePatterns.any { it.matches(fileName) }
}

private fun isInNodeModules(fileName: String): Boolean {
    return "/node_modules/" in fileName || "\\node_modules\\" in fileName
}

private val nodeModulesPattern = """[/\\]node_modules[/\\](@?[^/\\]+[/\\][^/\\]+|[^/\\]+)[/\\]""".toRegex()

private fun extractNodeModulesName(fileName: String): String? {
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

private fun computePackageForNodeModulesImport(
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
private fun Type.getPropertiesAsSymbols(): Array<Symbol> {
    val props = this.asDynamic().getProperties()
    if (props != null) return props.unsafeCast<Array<Symbol>>()

    val apparentProps = this.asDynamic().getApparentProperties()
    return if (apparentProps != null) apparentProps.unsafeCast<Array<Symbol>>() else emptyArray()
}

@Suppress("UNCHECKED_AS_TO_EXTERNAL_INTERFACE", "UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
private fun TypeChecker.getPropertyType(container: Type, symbol: Symbol): Type {
    // Use getTypeOfPropertyOfType which resolves the property type in the context
    // of the containing type (e.g. DecoderConfig for Container<DecoderConfig>.value),
    // unlike getTypeAtLocation(decl) which returns the unsubstituted type parameter T.
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
private fun resolveExportedDeclarationName(declaration: Node): String? {
    return when {
        isInterfaceDeclaration(declaration) -> declaration.name?.text
        isClassDeclaration(declaration) -> declaration.name?.text
        isTypeAliasDeclaration(declaration) -> declaration.name?.text
        isEnumDeclaration(declaration) -> declaration.name?.text
        else -> null
    }
}

private fun resolveNodeModulesImport(
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

        if (isInNodeModules(declSourceFileName)) {
            val resolved = resolveNodeModulesImport(declSourceFileName, typeName, context)
            if (resolved != null) {
                imports.add(resolved)
            }
        } else {
            val typeScriptService = context.lookupService(typeScriptServiceKey)!!
            val declNamespace = typeScriptService.findClosestNamespace(declNode)
            val declarationPackage = computePackage(declSourceFileName, declNamespace, context)

            if (declarationPackage != consumerPackage) {
                val fqn = resolveKotlinFqn(declNode, typeName, context)
                imports.add("import $fqn")
            }
        }
    }
}

@Suppress("UNCHECKED_AS_TO_EXTERNAL_INTERFACE", "UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
fun gatherImportsFromType(
    type: Type,
    consumerSourceFileName: String,
    consumerNamespace: ModuleDeclaration?,
    context: Context,
): List<String> {
    val typeScriptService = context.lookupService(typeScriptServiceKey) ?: return emptyList()
    val importInfoService = context.lookupService(importInfoServiceKey) ?: return emptyList()
    val typeChecker = typeScriptService.program.getTypeChecker()

    val consumerPackage = computePackage(consumerSourceFileName, consumerNamespace, context)
    val imports = mutableListOf<String>()
    val visited = mutableSetOf<Type>()

    type.getPropertiesAsSymbols().forEach { prop ->
        val propType = typeChecker.getPropertyType(type, prop)
        walkTypeAndGatherImports(propType, typeChecker, consumerPackage, context, imports, visited)
    }

    return imports.distinct()
}

@Suppress("UNCHECKED_AS_TO_EXTERNAL_INTERFACE", "UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
fun gatherImportsFromTypeReference(
    type: Type,
    consumerSourceFileName: String,
    consumerNamespace: ModuleDeclaration?,
    context: Context,
): List<String> {
    val typeScriptService = context.lookupService(typeScriptServiceKey) ?: return emptyList()
    val typeChecker = typeScriptService.program.getTypeChecker()

    val consumerPackage = computePackage(consumerSourceFileName, consumerNamespace, context)
    val imports = mutableListOf<String>()
    val visited = mutableSetOf<Type>()

    walkTypeAndGatherImports(type, typeChecker, consumerPackage, context, imports, visited)

    return imports.distinct()
}

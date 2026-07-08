package io.github.sgrishchenko.karakum.extension.plugins

import io.github.sgrishchenko.karakum.extension.*
import js.numbers.plus
import typescript.*
import kotlin.contracts.contract

@Suppress("CANNOT_CHECK_FOR_EXTERNAL_INTERFACE")
fun isInheritedTypeLiteral(node: Node): Boolean {
    contract {
        returns(true) implies (node is IntersectionTypeNode)
    }

    return isIntersectionTypeNode(node) && node.types.asArray().all {
        isTypeReferenceNode(it)
                || isTypeLiteralNode(it)
                || isMappedTypeNode(it)
    }
}

private fun detectPropertyConflicts(
    typeReferences: List<Node>,
    typeChecker: TypeChecker,
): Boolean {
    val propertyNamesByTypeRef = mutableListOf<Set<String>>()

    for (typeRef in typeReferences) {
        if (!isTypeReferenceNode(typeRef)) continue
        val type = typeChecker.getTypeFromTypeNode(typeRef.unsafeCast<TypeNode>())
        val properties = typeChecker.getPropertiesOfType(type)
        val names = mutableSetOf<String>()
        for (prop in properties) {
            names.add(prop.name)
        }
        propertyNamesByTypeRef.add(names)
    }

    val allNames = propertyNamesByTypeRef.flatten().toSet()
    for (name in allNames) {
        val typeRefsWithName = propertyNamesByTypeRef.count { it.contains(name) }
        if (typeRefsWithName >= 2) return true
    }

    return false
}

private suspend fun convertFlatInheritedTypeLiteral(
    node: IntersectionTypeNode,
    name: String,
    typeParameters: String?,
    inheritanceModifier: String?,
    externalModifier: String,
    heritageClauses: String?,
    injectedMembers: String,
    context: Context,
    render: Render<Node>,
): String {
    val typeScriptService = context.lookupService(typeScriptServiceKey)
    val typeChecker = typeScriptService?.program?.getTypeChecker()
    val checkCoverageService = context.lookupService(checkCoverageServiceKey)

    val allMembers = mutableListOf<String>()

    for (part in node.types.asArray()) {
        when {
            isTypeReferenceNode(part) -> {
                // First try resolving via typeScriptService (handles utility types)
                val resolved = typeScriptService?.resolveType(part, context, NodeBuilderFlags.NoTruncation + NodeBuilderFlags.InTypeAlias)
                if (resolved != null && isTypeLiteralNode(resolved)) {
                    checkCoverageService?.deepCover(resolved)
                    allMembers.addAll(resolved.members.asArray().map { render(it) })
                } else if (typeChecker != null) {
                    // Regular interface — get its declaration and render members
                    val type = typeChecker.getTypeFromTypeNode(part.unsafeCast<TypeNode>())
                    val symbol = type.symbol
                    val declaration = symbol?.valueDeclaration
                        ?: symbol?.declarations?.firstOrNull()
                    if (declaration != null && isInterfaceDeclaration(declaration)) {
                        allMembers.addAll(
                            (declaration as InterfaceDeclaration).members.asArray().map { render(it) }
                        )
                    }
                }
            }
            isTypeLiteralNode(part) -> {
                allMembers.addAll(part.members.asArray().map { render(it) })
            }
            isMappedTypeNode(part) -> {
                allMembers.add(convertMappedTypeBody(part, context, render))
            }
        }
    }

    val deduplicatedMembers = deduplicateMembers(allMembers)
    val body = deduplicatedMembers.joinToString(separator = "\n")

    return """
${ifPresent(inheritanceModifier) { "$it " }}${ifPresent(externalModifier) { "$it " }}interface ${name}${ifPresent(typeParameters) { "<${it}> " }}${(ifPresent(heritageClauses) { " : $it" })} {
$body${ifPresent(injectedMembers) { "\n${it}" }}
}
    """.trim()
}

private fun deduplicateMembers(members: List<String>): List<String> {
    val result = linkedMapOf<String, String>()
    for (member in members) {
        val name = member.trimStart()
            .removePrefix("var ")
            .removePrefix("val ")
            .removePrefix("fun ")
            .takeWhile { it != ':' && it != '(' && it != ' ' && it != '?' }
        if (name.isNotEmpty()) {
            result[name] = member
        } else {
            result[member] = member
        }
    }
    return result.values.toList()
}

suspend fun convertInheritedTypeLiteral(
    node: IntersectionTypeNode,
    name: String,
    typeParameters: String?,
    isInlined: Boolean,
    context: Context,
    render: Render<Node>,
): String {
    val checkCoverageService = context.lookupService(checkCoverageServiceKey)
    checkCoverageService?.cover(node)

    val typeScriptService = context.lookupService(typeScriptServiceKey)
    val namespaceInfoService = context.lookupService(namespaceInfoServiceKey)
    val inheritanceModifierService = context.lookupService(inheritanceModifierServiceKey)
    val injectionService = context.lookupService(injectionServiceKey)

    val inheritanceModifier = inheritanceModifierService?.resolveInheritanceModifier(node, context)
    val injections = injectionService?.resolveInjections(node, InjectionType.MEMBER, context, render)
    val heritageInjections = injectionService?.resolveInjections(node, InjectionType.HERITAGE_CLAUSE, context, render)

    val namespace = typeScriptService?.findClosestNamespace(node)

    val externalModifier = if (!isInlined) {
        "external"
    } else {
        (namespaceInfoService?.resolveExternalModifier(namespace) ?: "external")
    }

    val typeReferences = node.types.asArray().filter { isTypeReferenceNode(it) }
    val typeLiterals = node.types.asArray().filter { isTypeLiteralNode(it) }
    val mappedType = node.types.asArray().find { isMappedTypeNode(it) }

    val typeChecker = typeScriptService?.program?.getTypeChecker()
    val hasConflicts = typeChecker != null && typeReferences.size >= 2
            && detectPropertyConflicts(typeReferences, typeChecker)

    if (hasConflicts) {
        val injectedHeritageClauses = heritageInjections
            ?.filter { it.isNotEmpty() }
            ?.joinToString(separator = ", ")

        val injectedMembers = (injections ?: emptyArray<String>()).joinToString(separator = "\n")

        return convertFlatInheritedTypeLiteral(
            node, name, typeParameters,
            inheritanceModifier, externalModifier,
            injectedHeritageClauses, injectedMembers,
            context, render,
        )
    }

    val heritageTypes = typeReferences
        .map { render(it) }
        .filter { it.isNotEmpty() }
        .joinToString(separator = ", ")

    val injectedHeritageClauses = heritageInjections
        ?.filter { it.isNotEmpty() }
        ?.joinToString(separator = ", ")

    val fullHeritageClauses = arrayOf(heritageTypes, injectedHeritageClauses ?: "")
        .filter { it.isNotEmpty() }
        .joinToString(separator = ", ")

    val members = typeLiterals
        .mapNotNull {
            if (!isTypeLiteralNode(it)) return@mapNotNull null
            convertTypeLiteralBody(it, context, render)
        }
        .joinToString(separator = "\n")

    var accessors = ""

    if (mappedType != null && isMappedTypeNode(mappedType)) {
        accessors = convertMappedTypeBody(mappedType, context, render)
    }

    val injectedMembers = (injections ?: emptyArray()).joinToString(separator = "\n")

    return """
${ifPresent(inheritanceModifier) { "$it "}}${ifPresent(externalModifier) { "$it " }}interface ${name}${ifPresent(typeParameters) { "<${it}> "}}${(ifPresent(fullHeritageClauses) { " : $it"})} {
${ifPresent(accessors) { "${it}\n" }}${members}${ifPresent(injectedMembers) { "\n${it}"}}
}
    """.trim()
}

fun createInheritedTypeLiteralPlugin() = createAnonymousDeclarationPlugin plugin@{ node, context, render ->
    if (!isInheritedTypeLiteral(node)) return@plugin null

    val nameResolverService = context.requireService(nameResolverServiceKey)
    val name = nameResolverService.resolveName(node, context)

    val typeParameters = extractTypeParameters(node, context)

    val declaration =
        convertInheritedTypeLiteral(node, name, renderDeclaration(typeParameters, render), false, context, render)

    val reference = "${name}${ifPresent(renderReference(typeParameters, render)) { "<${it}>" }}"

    AnonymousDeclaration(
        name = name,
        declaration = declaration,
        reference = reference
    )
}

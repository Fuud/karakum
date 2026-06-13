package io.github.sgrishchenko.karakum.extension.plugins

import io.github.sgrishchenko.karakum.extension.*
import io.github.sgrishchenko.karakum.util.capitalize
import io.github.sgrishchenko.karakum.util.getParentOrNull
import typescript.Node
import typescript.TypeLiteralNode
import typescript.asArray
import typescript.isIdentifier
import typescript.isTypeLiteralNode
import typescript.isVariableDeclaration

suspend fun convertTypeLiteralBody(node: TypeLiteralNode, context: Context, render: Render<Node>): String {
    val checkCoverageService = context.lookupService(checkCoverageServiceKey)
    checkCoverageService?.cover(node)

    val injectionService = context.lookupService(injectionServiceKey)
    val injections = injectionService?.resolveInjections(node, InjectionType.MEMBER, context, render)

    val members = node.members.asArray()
        .map { render(it) }
        .joinToString(separator = "\n")

    val injectedMembers = (injections ?: emptyArray()).joinToString(separator = "\n")

    return "${members}${ifPresent(injectedMembers) { "\n${it}" }}"
}

suspend fun convertTypeLiteral(
    node: TypeLiteralNode,
    name: String,
    typeParameters: String?,
    isInlined: Boolean,
    context: Context,
    render: Render<Node>,
    companionObject: String = "",
): String {
    val typeScriptService = context.lookupService(typeScriptServiceKey)
    val namespaceInfoService = context.lookupService(namespaceInfoServiceKey)
    val inheritanceModifierService = context.lookupService(inheritanceModifierServiceKey)
    val inheritanceModifier = inheritanceModifierService?.resolveInheritanceModifier(node, context)

    val injectionService = context.lookupService(injectionServiceKey)
    val heritageInjections = injectionService?.resolveInjections(node, InjectionType.HERITAGE_CLAUSE, context, render)

    val namespace = typeScriptService?.findClosestNamespace(node)

    val externalModifier = if (!isInlined) {
        "external"
    } else {
        (namespaceInfoService?.resolveExternalModifier(namespace) ?: "external")
    }

    val injectedHeritageClauses = heritageInjections
        ?.filter { it.isNotEmpty() }
        ?.joinToString(separator = ", ")

    return """
${ifPresent(inheritanceModifier) { "$it "}}${ifPresent(externalModifier) { "$it " }}interface ${name}${ifPresent(typeParameters) { "<${it}>"}}${(ifPresent(injectedHeritageClauses) { ": $it"})} {
${convertTypeLiteralBody(node, context, render)}$companionObject
}
    """.trim()
}

fun createTypeLiteralPlugin() = createAnonymousDeclarationPlugin plugin@{ node, context, render ->
    if (!isTypeLiteralNode(node)) return@plugin null

    // handle empty type literal
    if (node.members.asArray().isEmpty()) return@plugin AnonymousDeclaration("Any")

    val nameResolverService = context.requireService(nameResolverServiceKey)
    val name = nameResolverService.resolveName(node, context)

    val typeParameters = extractTypeParameters(node, context)

    val companionObject = if (isSameNameVariableType(node, name, context)) {
        "\ncompanion object"
    } else {
        ""
    }

    val declaration = convertTypeLiteral(node, name, renderDeclaration(typeParameters, render), false, context, render, companionObject)

    val reference = "${name}${ifPresent(renderReference(typeParameters, render)) { "<${it}>" }}"

    AnonymousDeclaration(
        name = name,
        declaration = declaration,
        reference = reference
    )
}

private fun isSameNameVariableType(node: TypeLiteralNode, interfaceName: String, context: Context): Boolean {
    val typeScriptService = context.lookupService(typeScriptServiceKey) ?: return false
    val parent = typeScriptService.getParent(node) ?: node.getParentOrNull() ?: return false
    if (!isVariableDeclaration(parent)) return false

    val variableNameNode = parent.name
    if (!isIdentifier(variableNameNode)) return false

    return capitalize(variableNameNode.text) == interfaceName
}

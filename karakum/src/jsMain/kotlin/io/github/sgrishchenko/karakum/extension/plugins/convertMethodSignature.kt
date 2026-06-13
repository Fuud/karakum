package io.github.sgrishchenko.karakum.extension.plugins

import io.github.sgrishchenko.karakum.extension.createPlugin
import io.github.sgrishchenko.karakum.extension.ifPresent
import io.github.sgrishchenko.karakum.extension.renderNullable
import io.github.sgrishchenko.karakum.util.escapeIdentifier
import typescript.MethodSignature
import typescript.TypeNode
import typescript.asArray
import typescript.isIdentifier
import typescript.isMethodSignature
import typescript.isTypeLiteralNode

val convertMethodSignature = createPlugin plugin@{ node, context, render ->
    if (!isMethodSignature(node)) return@plugin null

    val checkCoverageService = context.lookupService(checkCoverageServiceKey)
    val typeScriptService = context.lookupService(typeScriptServiceKey)

    checkCoverageService?.cover(node)

    val inheritanceModifierService = context.lookupService(inheritanceModifierServiceKey)

    val name = escapeIdentifier(render(node.name))
    val annotation = createKebabAnnotation(node.name)

    val typeParameters = node.typeParameters?.asArray()
        ?.map { render(it) }
        ?.filter { it.isNotEmpty() }
        ?.joinToString(separator = ", ")

    val returnType = node.type?.let{ render(it) }

    val overrideDetectionService = context.lookupService(overrideDetectionServiceKey)

    // Eagerly resolve inheritance modifier to trigger detectOverrideModifier
    // which registers CompatibleOverrideInfo / NarrowingInfo as side effects
    inheritanceModifierService?.resolveSignatureInheritanceModifier(
        node, emptyArray(), context,
    )

    val narrowingInfo = overrideDetectionService?.getNarrowing(node)
    val compatibleOverrideInfo = overrideDetectionService?.getCompatibleOverride(node)

    val typeOverrides = buildTypeOverrides(node, compatibleOverrideInfo)
    if (typeOverrides.isNotEmpty()) {
        val checkCoverageService2 = context.lookupService(checkCoverageServiceKey)
        node.parameters.asArray().forEachIndexed { i, param ->
            val paramType = param.type
            if (i in typeOverrides && paramType != null && isTypeLiteralNode(paramType)) {
                checkCoverageService2?.deepCover(paramType)
            }
        }
    }

    if (node.questionToken != null) {
        return@plugin convertParameterDeclarations(
            node, context, render,
            ParameterDeclarationStrategy.lambda,
            typeOverrides = typeOverrides,
        ) { parameters, signature ->
            val inheritanceModifier =
                inheritanceModifierService?.resolveSignatureInheritanceModifier(node, signature, context)

            val functionType = if (node.typeParameters != null) {
                "Function<Any?> /* ${typeScriptService?.printNode(node)} */"
            } else if (node.parameters.asArray().any { parameter -> parameter.dotDotDotToken != null }) {
                "Function<${returnType}> /* ${typeScriptService?.printNode(node)} */"
            } else {
                "(${parameters}) -> ${returnType ?: "Any?"}"
            }

            "${ifPresent(annotation) { "${it}\n" }}${ifPresent(inheritanceModifier) { "$it "}}val ${name}: (${functionType})?"
        }
    }

    convertParameterDeclarations(
        node, context, render,
        ParameterDeclarationStrategy.function,
        typeOverrides = typeOverrides,
    ) { parameters, signature ->
        val inheritanceModifier =
            inheritanceModifierService?.resolveSignatureInheritanceModifier(node, signature, context)

        val result = "${ifPresent(annotation) { "${it}\n" }}${ifPresent(inheritanceModifier) { "$it "}}fun ${ifPresent(typeParameters) { "<${it}> " }}${name}(${parameters})${ifPresent(returnType) { ": $it" }}"

        if (narrowingInfo != null) {
            val baseParams = node.parameters.asArray().mapIndexed { i, param ->
                val paramName = param.name.let { name -> if (isIdentifier(name)) escapeIdentifier(name.text) else "param$i" }
                val baseType = narrowingInfo.baseParameterTypeNodes.getOrNull(i)
                val renderedBaseType = if (baseType != null) {
                    renderNullable(baseType, param.questionToken != null, context, render)
                } else {
                    "Any?"
                }
                "$paramName: $renderedBaseType"
            }.joinToString(separator = ", ")

            "override fun ${ifPresent(typeParameters) { "<${it}> " }}${name}(${baseParams})${ifPresent(returnType) { ": $it" }}\n$result"
        } else {
            result
        }
    }
}

private fun buildTypeOverrides(
    node: MethodSignature,
    compatibleOverrideInfo: CompatibleOverrideInfo?,
): Map<Int, TypeNode> {
    if (compatibleOverrideInfo == null) return emptyMap()
    val params = node.parameters.asArray()
    val overrides = mutableMapOf<Int, TypeNode>()
    for (i in params.indices) {
        val nodeParamType = params[i].type
        val baseParamType = compatibleOverrideInfo.baseParameterTypeNodes.getOrNull(i)
        if (nodeParamType != null && isTypeLiteralNode(nodeParamType) && baseParamType != null) {
            overrides[i] = baseParamType
        }
    }
    return overrides
}

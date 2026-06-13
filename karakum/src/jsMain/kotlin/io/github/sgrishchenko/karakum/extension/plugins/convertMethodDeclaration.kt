package io.github.sgrishchenko.karakum.extension.plugins

import io.github.sgrishchenko.karakum.extension.createPlugin
import io.github.sgrishchenko.karakum.extension.ifPresent
import io.github.sgrishchenko.karakum.extension.renderNullable
import io.github.sgrishchenko.karakum.util.escapeIdentifier
import typescript.SyntaxKind
import typescript.asArray
import typescript.isIdentifier
import typescript.isMethodDeclaration

val convertMethodDeclaration = createPlugin plugin@{ node, context, render ->
    if (!isMethodDeclaration(node)) return@plugin null

    val checkCoverageService = context.lookupService(checkCoverageServiceKey)
    val typeScriptService = context.lookupService(typeScriptServiceKey)

    checkCoverageService?.cover(node)

    val inheritanceModifierService = context.lookupService(inheritanceModifierServiceKey)

    val name = escapeIdentifier(render(node.name))
    val annotation = createKebabAnnotation(node.name)

    val abstractModifier = "abstract".takeIf {
        node.modifiers?.asArray()?.any {
            if (it.kind == SyntaxKind.AbstractKeyword) {
                checkCoverageService?.cover(it)
                true
            } else false
        } ?: false
    }

    val typeParameters = node.typeParameters?.asArray()
        ?.map { render(it) }
        ?.filter { it.isNotEmpty() }
        ?.joinToString(separator = ", ")

    val returnType = node.type?.let{ render(it) }

    if (node.questionToken != null) {
        return@plugin convertParameterDeclarations(
            node, context, render,
            ParameterDeclarationStrategy.lambda,
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
    ) { parameters, signature ->
        val inheritanceModifier =
            inheritanceModifierService?.resolveSignatureInheritanceModifier(node, signature, context)

        val result = "${ifPresent(annotation) { "${it}\n" }}${ifPresent(inheritanceModifier) { "$it "}}${ifPresent(abstractModifier) { "$it "}}fun ${ifPresent(typeParameters) { "<${it}> " }}${name}(${parameters})${ifPresent(returnType) { ": $it" }}"

        val overrideDetectionService = context.lookupService(overrideDetectionServiceKey)
        val narrowingInfo = overrideDetectionService?.getNarrowing(node)

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

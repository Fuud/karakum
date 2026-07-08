package io.github.sgrishchenko.karakum.extension.plugins

import io.github.sgrishchenko.karakum.extension.createPlugin
import io.github.sgrishchenko.karakum.extension.ifPresent
import io.github.sgrishchenko.karakum.extension.renderNullable
import io.github.sgrishchenko.karakum.util.escapeIdentifier
import typescript.SyntaxKind
import typescript.asArray
import typescript.isPropertySignature

val convertPropertySignature = createPlugin plugin@{ node, context, render ->
    if (!isPropertySignature(node)) return@plugin null

    val checkCoverageService = context.lookupService(checkCoverageServiceKey)

    checkCoverageService?.cover(node)

    val inheritanceModifierService = context.lookupService(inheritanceModifierServiceKey)
    val mutabilityModifierService = context.lookupService(mutabilityModifierServiceKey)

    val inheritanceModifier = inheritanceModifierService?.resolveInheritanceModifier(node, context)
    val mutabilityModifier = mutabilityModifierService?.resolveMutabilityModifier(node, context)

    val readonly = node.modifiers?.asArray()?.find { modifier -> modifier.kind == SyntaxKind.ReadonlyKeyword }
    readonly?.let { checkCoverageService?.cover(it) }

    node.questionToken?.let { checkCoverageService?.cover(it) }

    val modifier = mutabilityModifier ?: if (readonly != null) "val" else "var"

    val rawName = render(node.name)
    val name = escapeIdentifier(rawName)
    val annotation = createKebabAnnotation(node.name, context)

    val isOptional = node.questionToken != null

    val type = renderNullable(node.type, isOptional, context, render)

    val result = "${ifPresent(annotation) { "${it}\n" }}${ifPresent(inheritanceModifier) { "$it "}}${modifier} ${name}: $type"

    val overrideDetectionService = context.lookupService(overrideDetectionServiceKey)
    val narrowingInfo = overrideDetectionService?.getNarrowing(node)

    if (narrowingInfo != null && narrowingInfo.basePropertyTypeNode != null) {
        registerBaseTypeImports(node, listOf(narrowingInfo.basePropertyTypeNode), context)
        val baseType = renderNullable(narrowingInfo.basePropertyTypeNode, narrowingInfo.baseIsOptional, context, render)
        val narrowedJsName = annotation.takeIf { it.isNotEmpty() } ?: "@JsName(\"$rawName\")"
        val narrowedName = escapeIdentifier("${rawName}Narrowed")
        val overrideDecl = "${ifPresent(annotation) { "$it\n" }}override ${modifier} ${name}: $baseType"
        val narrowedDecl = "$narrowedJsName\n${modifier} ${narrowedName}: $type"
        "$overrideDecl\n$narrowedDecl"
    } else {
        result
    }
}

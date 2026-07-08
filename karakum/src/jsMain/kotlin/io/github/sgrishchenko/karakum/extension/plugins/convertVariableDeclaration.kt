package io.github.sgrishchenko.karakum.extension.plugins

import io.github.sgrishchenko.karakum.configuration.NamespaceStrategy
import io.github.sgrishchenko.karakum.configuration.`package`
import io.github.sgrishchenko.karakum.extension.createPlugin
import io.github.sgrishchenko.karakum.extension.ifPresent
import io.github.sgrishchenko.karakum.util.getSourceFileOrNull
import js.numbers.contains
import typescript.NodeFlags
import typescript.asArray
import typescript.isExportAssignment
import typescript.isIdentifier
import typescript.isVariableDeclaration

private fun isDefaultExportVariable(node: typescript.VariableDeclaration, context: io.github.sgrishchenko.karakum.extension.Context): Boolean {
    val name = node.name
    if (!isIdentifier(name)) return false

    val sourceFile = node.getSourceFileOrNull() ?: return false

    return sourceFile.statements.asArray().any { statement ->
        if (!isExportAssignment(statement)) return@any false
        if (statement.isExportEquals == true) return@any false
        val expression = statement.expression
        isIdentifier(expression) && expression.text == name.text
    }
}

val convertVariableDeclaration = createPlugin plugin@{ node, context, render ->
    if (!isVariableDeclaration(node)) return@plugin null

    val checkCoverageService = context.lookupService(checkCoverageServiceKey)
    checkCoverageService?.cover(node)

    val declarationMergingService = context.lookupService(declarationMergingServiceKey)
    if (declarationMergingService?.isMergedWithInterface(node) == true) return@plugin ""

    if (isDefaultExportVariable(node, context)) {
        node.initializer?.let { checkCoverageService?.cover(it) }
        return@plugin ""
    }

    // skip initializer
    node.initializer?.let { checkCoverageService?.cover(it) }

    val commentService = context.lookupService(commentServiceKey)
    val typeScriptService = context.lookupService(typeScriptServiceKey)
    val namespaceInfoService = context.lookupService(namespaceInfoServiceKey)
    val mutabilityModifierService = context.lookupService(mutabilityModifierServiceKey)

    val mutabilityModifier = mutabilityModifierService?.resolveMutabilityModifier(node, context)

    val modifier = mutabilityModifier ?: if (NodeFlags.Const in node.parent.flags) {
        "val"
    } else {
        "var"
    }

    val name = render(node.name)

    val namespace = typeScriptService?.findClosestNamespace(node)

    val externalModifier = namespaceInfoService?.resolveExternalModifier(namespace) ?: "external"

    var leadingComment = ""

    if (
        namespace == null
        || namespaceInfoService?.resolveNamespaceStrategy(namespace) == NamespaceStrategy.`package`
    ) {
        leadingComment = commentService?.renderLeadingComments(node.parent) ?: ""
    }

    val type = node.type
        ?.let { render(it) }
        ?: "Any? /* should be inferred */" // TODO: infer types

    // When the type resolves to the same name as the variable (e.g. const Links: { ... }
    // where the TypeLiteralPlugin generates interface Links), the variable would conflict
    // with the generated interface. The interface already has a companion object for JS value access.
    if (name == type) return@plugin ""

    "${leadingComment}${ifPresent(externalModifier) { "$it " }}${modifier} ${name}: $type"
}

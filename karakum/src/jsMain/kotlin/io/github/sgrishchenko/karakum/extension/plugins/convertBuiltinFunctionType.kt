package io.github.sgrishchenko.karakum.extension.plugins

import io.github.sgrishchenko.karakum.extension.createPlugin
import io.github.sgrishchenko.karakum.extension.isBuiltin
import typescript.isIdentifier
import typescript.isTypeReferenceNode

val convertBuiltinFunctionType = createPlugin plugin@{ node, context, _ ->
    if (!isTypeReferenceNode(node)) return@plugin null

    val typeName = node.typeName
    if (!isIdentifier(typeName)) return@plugin null
    if (typeName.text != "Function") return@plugin null
    if (!isBuiltin(typeName, context)) return@plugin null

    val checkCoverageService = context.lookupService(checkCoverageServiceKey)
    val typeScriptService = context.lookupService(typeScriptServiceKey)

    checkCoverageService?.cover(node)
    checkCoverageService?.cover(typeName)

    "Function<*> /* ${typeScriptService?.printNode(node)} */"
}

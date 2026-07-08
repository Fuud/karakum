package io.github.sgrishchenko.karakum.extension.plugins

import io.github.sgrishchenko.karakum.extension.createPlugin
import io.github.sgrishchenko.karakum.util.getParentOrNull
import typescript.isExpressionWithTypeArguments
import typescript.isIdentifier
import typescript.isTypeReferenceNode

val convertConfiguredTypeReference = createPlugin plugin@{ node, context, _ ->
    if (!isIdentifier(node)) return@plugin null

    val parent = node.getParentOrNull() ?: return@plugin null
    if (
        !isTypeReferenceNode(parent)
        && !isExpressionWithTypeArguments(parent)
    ) return@plugin null

    val configurationService = context.lookupService(configurationServiceKey) ?: return@plugin null
    val typeMapper = configurationService.configuration.typeMapper

    for ((pattern, mapping) in typeMapper) {
        if (pattern.toRegex().containsMatchIn(node.text)) {
            val checkCoverageService = context.lookupService(checkCoverageServiceKey)
            checkCoverageService?.cover(node)

            return@plugin if (mapping.endsWith(".")) {
                "$mapping${node.text}"
            } else {
                mapping
            }
        }
    }

    null
}

package io.github.sgrishchenko.karakum.extension.nameResolvers

import io.github.sgrishchenko.karakum.extension.NameResolver
import io.github.sgrishchenko.karakum.extension.plugins.utilityTypeNameServiceKey
import typescript.Node

val resolveUtilityTypeName: NameResolver = { node: Node, context ->
    val service = context.lookupService(utilityTypeNameServiceKey)
    service?.lookupName(node)
}

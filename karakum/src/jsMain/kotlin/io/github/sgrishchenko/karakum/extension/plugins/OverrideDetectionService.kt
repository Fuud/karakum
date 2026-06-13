package io.github.sgrishchenko.karakum.extension.plugins

import io.github.sgrishchenko.karakum.extension.Context
import io.github.sgrishchenko.karakum.extension.ContextKey
import io.github.sgrishchenko.karakum.extension.Plugin
import typescript.Declaration
import typescript.Node
import typescript.TypeNode

val overrideDetectionServiceKey = ContextKey<OverrideDetectionService>()

class NarrowingInfo(
    val baseDeclaration: Declaration,
    val baseParameterTypeNodes: Array<TypeNode?>,
    val basePropertyTypeNode: TypeNode?,
)

class OverrideDetectionService {
    private val narrowings = mutableMapOf<Node, NarrowingInfo>()

    fun registerNarrowing(node: Node, info: NarrowingInfo) {
        narrowings[node] = info
    }

    fun getNarrowing(node: Node): NarrowingInfo? = narrowings[node]
}

class OverrideDetectionPlugin : Plugin {
    override suspend fun setup(context: Context) {
        context.registerService(overrideDetectionServiceKey, OverrideDetectionService())
    }

    override suspend fun traverse(node: Node, context: Context) = Unit

    override suspend fun render(node: Node, context: Context, next: io.github.sgrishchenko.karakum.extension.Render<Node>) = null

    override suspend fun generate(context: Context, render: io.github.sgrishchenko.karakum.extension.Render<Node>) = emptyArray<io.github.sgrishchenko.karakum.extension.GeneratedFile>()
}

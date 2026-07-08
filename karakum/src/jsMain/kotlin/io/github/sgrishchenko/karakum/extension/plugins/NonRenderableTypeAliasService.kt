package io.github.sgrishchenko.karakum.extension.plugins

import io.github.sgrishchenko.karakum.extension.Context
import io.github.sgrishchenko.karakum.extension.ContextKey
import io.github.sgrishchenko.karakum.extension.GeneratedFile
import io.github.sgrishchenko.karakum.extension.Plugin
import io.github.sgrishchenko.karakum.extension.Render
import js.array.ReadonlyArray
import typescript.Node
import typescript.Symbol

val nonRenderableTypeAliasServiceKey = ContextKey<NonRenderableTypeAliasService>()

class NonRenderableTypeAliasService {
    private val nonRenderable = mutableSetOf<Symbol>()

    fun register(symbol: Symbol) {
        nonRenderable.add(symbol)
    }

    fun isNonRenderable(symbol: Symbol): Boolean = symbol in nonRenderable
}

class NonRenderableTypeAliasServicePlugin : Plugin {
    override suspend fun setup(context: Context) {
        context.registerService(nonRenderableTypeAliasServiceKey, NonRenderableTypeAliasService())
    }

    override suspend fun traverse(node: Node, context: Context) = Unit

    override suspend fun render(node: Node, context: Context, next: Render<Node>): String? = null

    override suspend fun generate(context: Context, render: Render<Node>): ReadonlyArray<GeneratedFile> = emptyArray()
}

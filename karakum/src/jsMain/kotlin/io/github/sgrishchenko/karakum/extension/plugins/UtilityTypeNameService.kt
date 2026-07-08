package io.github.sgrishchenko.karakum.extension.plugins

import io.github.sgrishchenko.karakum.extension.Context
import io.github.sgrishchenko.karakum.extension.ContextKey
import io.github.sgrishchenko.karakum.extension.GeneratedFile
import io.github.sgrishchenko.karakum.extension.Plugin
import io.github.sgrishchenko.karakum.extension.Render
import js.array.ReadonlyArray
import typescript.Node
import typescript.Type

val utilityTypeNameServiceKey = ContextKey<UtilityTypeNameService>()

class UtilityTypeNameService {
    private val typeToName = mutableMapOf<Type, String>()

    fun registerName(type: Type, name: String) {
        typeToName[type] = name
    }

    fun lookupName(type: Type): String? = typeToName[type]
}

class UtilityTypeNameServicePlugin : Plugin {
    override suspend fun setup(context: Context) {
        context.registerService(utilityTypeNameServiceKey, UtilityTypeNameService())
    }

    override suspend fun traverse(node: Node, context: Context) = Unit

    override suspend fun render(node: Node, context: Context, next: Render<Node>): String? = null

    override suspend fun generate(context: Context, render: Render<Node>): ReadonlyArray<GeneratedFile> = emptyArray()
}

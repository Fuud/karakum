package io.github.sgrishchenko.karakum.extension.plugins

import io.github.sgrishchenko.karakum.extension.*
import io.github.sgrishchenko.karakum.structure.import.ImportInfo
import js.array.ReadonlyArray
import typescript.ModuleDeclaration
import typescript.Node
import typescript.Program
import typescript.isImportDeclaration

@JsExport
val importInfoServiceKey = ContextKey<ImportInfoService>()

@JsExport
class ImportInfoService @JsExport.Ignore constructor(
    private val program: Program,
    private val importInfo: ImportInfo,
) {
    private val dynamicImports = mutableMapOf<Pair<String?, ModuleDeclaration?>, MutableSet<String>>()

    fun resolveImports(sourceFileName: String, node: ModuleDeclaration?): ReadonlyArray<String> {
        val staticImports = if (node != null) {
            importInfo[node] ?: emptyArray()
        } else {
            val sourcefile = program.getSourceFile(sourceFileName) ?: return emptyArray()
            importInfo[sourcefile] ?: emptyArray()
        }

        val key = Pair<String?, ModuleDeclaration?>(sourceFileName, node)
        val dynImports = dynamicImports[key] ?: emptySet()

        return (staticImports + dynImports).toList().distinct().toTypedArray()
    }

    fun addDynamicImport(sourceFileName: String, namespace: ModuleDeclaration?, importStatement: String) {
        // Skip if this import already exists in static imports
        val staticImports = if (namespace != null) {
            importInfo[namespace] ?: emptyArray()
        } else {
            val sourcefile = program.getSourceFile(sourceFileName) ?: return
            importInfo[sourcefile] ?: emptyArray()
        }
        if (importStatement in staticImports) return

        val key = Pair<String?, ModuleDeclaration?>(sourceFileName, namespace)
        dynamicImports.getOrPut(key) { mutableSetOf() }.add(importStatement)
    }

    fun resolveDynamicImports(sourceFileName: String, node: ModuleDeclaration?): ReadonlyArray<String> {
        val key = Pair<String?, ModuleDeclaration?>(sourceFileName, node)
        return dynamicImports[key]?.toTypedArray() ?: emptyArray()
    }
}

class ImportInfoPlugin(program: Program, importInfo: ImportInfo) : Plugin {
    private val importInfoService = ImportInfoService(program, importInfo)

    override suspend fun setup(context: Context) {
        context.registerService(importInfoServiceKey, importInfoService)
    }

    override suspend fun traverse(node: Node, context: Context) = Unit

    override suspend fun render(node: Node, context: Context, next: Render<Node>): String? {
        if (isImportDeclaration(node)) {
            val checkCoverageService = context.lookupService(checkCoverageServiceKey)
            checkCoverageService?.deepCover(node)

            return ""
        }

        return null
    }

    override suspend fun generate(context: Context, render: Render<Node>) = emptyArray<GeneratedFile>()
}

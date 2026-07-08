package io.github.sgrishchenko.karakum.extension.plugins

import io.github.sgrishchenko.karakum.extension.*
import io.github.sgrishchenko.karakum.util.getParentOrNull
import io.github.sgrishchenko.karakum.util.getSourceFileOrNull
import io.github.sgrishchenko.karakum.util.setParentNodes
import js.numbers.plus
import typescript.*

@JsExport
val typeScriptServiceKey = ContextKey<TypeScriptService>()

@JsExport
class TypeScriptService @JsExport.Ignore constructor(val program: Program) {
    private val virtualSourceFile = createSourceFile("virtual.d.ts", "", ScriptTarget.Latest)
    private val printer = createPrinter(PrinterOptions(
        removeComments = true,
        newLine = NewLineKind.LineFeed,
    ))
    private val virtualParents = mutableMapOf<Node, Node>()
    private val virtualSourceFiles = mutableMapOf<Node, SourceFile>()

    fun printNode(node: Node): String {
        val sourceFile = node.getSourceFileOrNull() ?: this.virtualSourceFile

        return printer.printNode(EmitHint.Unspecified, node, sourceFile)
    }

    fun getParent(node: Node): Node? {
        val realParent = node.getParentOrNull()
        if (realParent != null) return realParent

        return virtualParents[node]
    }

    fun findClosest(rootNode: Node?, predicate: (node: Node) -> Boolean): Node? {
        if (rootNode == null) return null
        if (predicate(rootNode)) return rootNode

        return findClosest(getParent(rootNode), predicate)
    }

    @Suppress("UNCHECKED_AS_TO_EXTERNAL_INTERFACE")
    fun findClosestNamespace(rootNode: Node?): ModuleDeclaration? {
        return findClosest(rootNode, ::isModuleDeclaration) as ModuleDeclaration?
    }

    fun resolveType(node: TypeNode, context: Context? = null, flags: NodeBuilderFlags = NodeBuilderFlags.NoTruncation): Node? {
        val typeChecker = program.getTypeChecker()
        val sourceFile = node.getSourceFileOrNull()
        val type = typeChecker.getTypeAtLocation(node)

        // Gather imports from the Type object BEFORE typeToTypeNode
        if (context != null && sourceFile != null) {
            val importInfoService = context.lookupService(importInfoServiceKey)
            if (importInfoService != null) {
                val sourceFileName = sourceFile.fileName
                val namespace = findClosestNamespace(node)
                val gatheredImports = gatherImportsFromType(type, sourceFileName, namespace, context)
                for (importStatement in gatheredImports) {
                    importInfoService.addDynamicImport(sourceFileName, namespace, importStatement)
                }

                // Also gather imports from TypeReferenceNode type arguments.
                // When TypeScript expands utility types like Partial<Container<DecoderConfig>>,
                // the resulting Type is an anonymous expanded type without ObjectFlags.Reference,
                // so walkType can't find type arguments. Walk the AST node's type arguments instead.
                if (isTypeReferenceNode(node)) {
                    val typeArgs = node.typeArguments
                    if (typeArgs != null) {
                        typeArgs.asArray().forEach { typeArg ->
                            val typeArgType = typeChecker.getTypeAtLocation(typeArg)
                            val typeArgImports = gatherImportsFromType(typeArgType, sourceFileName, namespace, context)
                            typeArgImports.forEach { importStatement ->
                                importInfoService.addDynamicImport(sourceFileName, namespace, importStatement)
                            }
                        }
                    }
                }
            }
        }

        val typeNode = typeChecker.typeToTypeNode(type, undefined, flags)

        val parent = getParent(node)
        if (typeNode != null) {
            if (parent != null) {
                this.virtualParents[typeNode] = parent
            }
            if (sourceFile != null) {
                this.virtualSourceFiles[typeNode] = sourceFile
            } else {
                var ancestor: Node? = node
                var ancestorSourceFile: SourceFile? = null
                while (ancestor != null && ancestorSourceFile == null) {
                    ancestorSourceFile = this.virtualSourceFiles[ancestor]
                    ancestor = getParent(ancestor)
                }
                if (ancestorSourceFile != null) {
                    this.virtualSourceFiles[typeNode] = ancestorSourceFile
                }
            }
            setParentNodes(typeNode)
        }

        return typeNode
    }

    fun getSourceFile(node: Node): SourceFile? {
        val realSourceFile = node.getSourceFileOrNull()
        if (realSourceFile != null) return realSourceFile

        val direct = virtualSourceFiles[node]
        if (direct != null) return direct

        var ancestor: Node? = getParent(node)
        while (ancestor != null) {
            val ancestorSourceFile = virtualSourceFiles[ancestor]
            if (ancestorSourceFile != null) return ancestorSourceFile
            ancestor = getParent(ancestor)
        }

        return null
    }
}

class TypeScriptPlugin(program: Program) : Plugin {
    private val typeScriptService = TypeScriptService(program)

    override suspend fun generate(context: Context, render: Render<Node>) = emptyArray<GeneratedFile>()

    override suspend fun render(node: Node, context: Context, next: Render<Node>) = null

    override suspend fun traverse(node: Node, context: Context) = Unit

    override suspend fun setup(context: Context) {
        context.registerService(typeScriptServiceKey, this.typeScriptService)
    }
}

package io.github.sgrishchenko.karakum.extension.plugins

import io.github.sgrishchenko.karakum.extension.*
import io.github.sgrishchenko.karakum.util.getParentOrNull
import io.github.sgrishchenko.karakum.util.getSourceFileOrNull
import io.github.sgrishchenko.karakum.util.setParentNodes
import js.array.asArray
import js.numbers.contains
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

    fun resolveType(node: Node): Node? {
        val typeChecker = program.getTypeChecker()

        val sourceFile = node.getSourceFileOrNull()
        val type = if (sourceFile != null) {
            typeChecker.getTypeAtLocation(node)
        } else {
            // Virtual nodes (from typeToTypeNode) have no source file,
            // so getTypeAtLocation returns 'any'.
            // Try getTypeFromTypeNode first, and if that returns 'any',
            // search for a matching original node in source files.
            val virtualType: Type = if (isTypeNode(node)) {
                typeChecker.getTypeFromTypeNode(node.unsafeCast<TypeNode>())
            } else {
                typeChecker.getTypeAtLocation(node)
            }

            if (TypeFlags.Any !in virtualType.flags) {
                virtualType
            } else {
                resolveTypeFromOriginalNode(node, typeChecker) ?: virtualType
            }
        }

        val flags = NodeBuilderFlags.NoTruncation + NodeBuilderFlags.InTypeAlias
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

    @Suppress("UNCHECKED_AS_TO_EXTERNAL_INTERFACE")
    private fun resolveTypeFromOriginalNode(node: Node, typeChecker: TypeChecker): Type? {
        if (!isTypeReferenceNode(node)) return null

        val typeName = node.typeName
        if (!isIdentifier(typeName)) return null

        val typeArguments = node.typeArguments?.asArray() ?: return null
        if (typeArguments.isEmpty()) return null

        val typeArgNames = typeArguments.mapNotNull { arg ->
            if (isIdentifier(arg)) arg.text
            else {
                val argTypeName = if (isTypeReferenceNode(arg)) arg.typeName else null
                if (argTypeName != null && isIdentifier(argTypeName)) argTypeName.text
                else null
            }
        }.toSet()

        if (typeArgNames.isEmpty()) return null

        // Try to find a matching original TypeReferenceNode in source files
        // and resolve through it, since virtual nodes have no symbols for typeChecker.
        val contextSourceFile = this.virtualSourceFiles[node]
        val sourceFiles = if (contextSourceFile != null) {
            // Prefer the context source file, but also check others
            arrayOf(contextSourceFile) + program.getSourceFiles().filter { it.fileName != contextSourceFile.fileName }
        } else {
            program.getSourceFiles()
        }

        for (sf in sourceFiles) {
            val originalNode = findMatchingTypeReference(sf, typeName.text, typeArgNames)
            if (originalNode != null) {
                return typeChecker.getTypeAtLocation(originalNode)
            }
        }

        return null
    }

    @Suppress("UNCHECKED_AS_TO_EXTERNAL_INTERFACE")
    private fun findMatchingTypeReference(sourceFile: SourceFile, typeName: String, typeArgNames: Set<String>): TypeReferenceNode? {
        var result: TypeReferenceNode? = null

        fun visit(node: Node) {
            if (result != null) return

            if (isTypeReferenceNode(node)) {
                val tn = node.typeName
                if (isIdentifier(tn) && tn.text == typeName) {
                    val args = node.typeArguments?.asArray()
                    if (args != null && args.isNotEmpty()) {
                        val argNames = args.mapNotNull { arg ->
                            if (isIdentifier(arg)) arg.text
                            else {
                                val argTypeName = if (isTypeReferenceNode(arg)) arg.typeName else null
                                if (argTypeName != null && isIdentifier(argTypeName)) argTypeName.text
                                else null
                            }
                        }.toSet()

                        if (argNames == typeArgNames) {
                            result = node.unsafeCast<TypeReferenceNode>()
                            return
                        }
                    }
                }
            }

            node.forEachChild({ child ->
                visit(child)
                undefined
            })
        }

        val sfNode = sourceFile.unsafeCast<Node>()
        sfNode.forEachChild({ child ->
            visit(child)
            undefined
        })

        return result
    }

    fun getSourceFile(node: Node): SourceFile? {
        val realSourceFile = node.getSourceFileOrNull()
        if (realSourceFile != null) return realSourceFile

        return virtualSourceFiles[node]
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

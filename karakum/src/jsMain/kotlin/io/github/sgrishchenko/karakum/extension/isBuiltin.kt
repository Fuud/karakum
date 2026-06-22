package io.github.sgrishchenko.karakum.extension

import io.github.sgrishchenko.karakum.extension.plugins.typeScriptServiceKey
import io.github.sgrishchenko.karakum.util.getSourceFileOrNull
import js.array.asArray
import typescript.Node
import typescript.SourceFile
import typescript.TypeReferenceNode
import typescript.isIdentifier
import typescript.isTypeReferenceNode

private val builtinSources = listOf(
    """^.*/typescript/lib/lib\.decorators\.d\.ts$""".toRegex(),
    """^.*/typescript/lib/lib\.dom\.d\.ts$""".toRegex(),
    """^.*/typescript/lib/lib\.es.+\.d\.ts$""".toRegex(),
    """^.*/typescript/lib/lib\.scripthost\.d\.ts$""".toRegex(),
    """^.*/typescript/lib/lib\.webworker\.d\.ts$""".toRegex(),
    """^.*/typescript/lib/lib\.webworker\.importscripts\.d\.ts$""".toRegex(),
)

private fun checkBuiltinDeclarations(symbol: dynamic): Boolean {
    val decls = (symbol.declarations as Array<Node>?).orEmpty().toList()
    val valueDecl = symbol.valueDeclaration as Node?

    return (decls + listOfNotNull(valueDecl)).any { declaration ->
        val sourceFileName = declaration.getSourceFileOrNull()?.fileName
        sourceFileName != null && builtinSources.any { it.matches(sourceFileName) }
    }
}

@JsExport
fun isBuiltin(node: Node, context: Context): Boolean {
    val typeScriptService = context.lookupService(typeScriptServiceKey) ?: return false
    val typeChecker = typeScriptService.program.getTypeChecker()

    val symbol = typeChecker.getSymbolAtLocation(node)
    if (symbol != null) return checkBuiltinDeclarations(symbol)

    // Virtual nodes (from typeToTypeNode) have no symbol — getSymbolAtLocation returns null.
    // Find the original node with the same identifier text in source files
    // and check its symbol instead.
    if (!isIdentifier(node)) return false
    val identifierText = node.text

    val contextSourceFile = typeScriptService.getSourceFile(node)
    val sourceFiles = if (contextSourceFile != null) {
        arrayOf(contextSourceFile) + typeScriptService.program.getSourceFiles().filter { it.fileName != contextSourceFile.fileName }
    } else {
        typeScriptService.program.getSourceFiles()
    }

    for (sf in sourceFiles) {
        val originalNode = findIdentifierUsage(sf, identifierText)
        if (originalNode != null) {
            val originalSymbol = typeChecker.getSymbolAtLocation(originalNode)
            if (originalSymbol != null && checkBuiltinDeclarations(originalSymbol)) return true
        }
    }

    return false
}

@Suppress("UNCHECKED_AS_TO_EXTERNAL_INTERFACE")
private fun findIdentifierUsage(sourceFile: SourceFile, identifierText: String): Node? {
    var result: Node? = null

    fun visit(node: Node) {
        if (result != null) return

        if (isIdentifier(node) && node.text == identifierText) {
            // Only match identifiers used as type names (not property names, etc.)
            val parent = node.parent
            if (parent != null && isTypeReferenceNode(parent)) {
                val tn = parent.typeName
                if (isIdentifier(tn) && tn.text == identifierText) {
                    result = node
                    return
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

package io.github.sgrishchenko.karakum.extension.plugins

import io.github.sgrishchenko.karakum.extension.InheritanceModifier
import typescript.*

private fun isAbstractMember(node: Node): Boolean {
    val modifiers = when {
        isClassDeclaration(node) -> (node as ClassDeclaration).modifiers
        isMethodDeclaration(node) -> (node as MethodDeclaration).modifiers
        isPropertyDeclaration(node) -> (node as PropertyDeclaration).modifiers
        isGetAccessor(node) -> (node as GetAccessorDeclaration).modifiers
        isSetAccessor(node) -> (node as SetAccessorDeclaration).modifiers
        else -> return false
    }
    return modifiers?.asArray()?.any { it.kind == SyntaxKind.AbstractKeyword } ?: false
}

private fun isStaticMember(node: Node): Boolean {
    val modifiers = when {
        isMethodDeclaration(node) -> (node as MethodDeclaration).modifiers
        isPropertyDeclaration(node) -> (node as PropertyDeclaration).modifiers
        isGetAccessor(node) -> (node as GetAccessorDeclaration).modifiers
        isSetAccessor(node) -> (node as SetAccessorDeclaration).modifiers
        else -> return false
    }
    return modifiers?.asArray()?.any { it.kind == SyntaxKind.StaticKeyword } ?: false
}

private fun isInterfaceMember(node: Node): Boolean {
    if (isMethodSignature(node) || isPropertySignature(node)) return true
    if (isGetAccessor(node) || isSetAccessor(node)) {
        val parent = node.parent
        return isInterfaceDeclaration(parent)
    }
    return false
}

val detectOpenModifier: InheritanceModifier = { node, _ ->
    when {
        isClassDeclaration(node) && !isAbstractMember(node) -> "open"
        (isMethodDeclaration(node) || isPropertyDeclaration(node))
            && !isAbstractMember(node) && !isStaticMember(node) -> "open"
        (isGetAccessor(node) || isSetAccessor(node))
            && !isAbstractMember(node) && !isInterfaceMember(node) -> "open"
        else -> null
    }
}

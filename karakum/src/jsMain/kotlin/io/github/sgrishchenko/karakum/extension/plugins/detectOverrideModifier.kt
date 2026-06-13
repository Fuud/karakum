package io.github.sgrishchenko.karakum.extension.plugins

import io.github.sgrishchenko.karakum.extension.InheritanceModifier
import typescript.*

private fun getMemberName(node: Node): String? {
    val name = when {
        isMethodDeclaration(node) -> (node as MethodDeclaration).name
        isPropertyDeclaration(node) -> (node as PropertyDeclaration).name
        isGetAccessor(node) -> (node as GetAccessorDeclaration).name
        isSetAccessor(node) -> (node as SetAccessorDeclaration).name
        isMethodSignature(node) -> (node as MethodSignature).name
        isPropertySignature(node) -> (node as PropertySignature).name
        else -> return null
    }
    return if (isIdentifier(name)) name.text else null
}

private fun isStaticMember(node: Node): Boolean {
    val modifiers = when {
        isMethodDeclaration(node) -> (node as MethodDeclaration).modifiers
        isPropertyDeclaration(node) -> (node as PropertyDeclaration).modifiers
        isGetAccessor(node) -> (node as GetAccessorDeclaration).modifiers
        isSetAccessor(node) -> (node as SetAccessorDeclaration).modifiers
        else -> null
    }
    return modifiers?.asArray()?.any { it.kind == SyntaxKind.StaticKeyword } ?: false
}

private fun collectBaseSymbols(
    parentNode: Node,
    typeChecker: TypeChecker,
): Map<String, Symbol> {
    val result = mutableMapOf<String, Symbol>()

    val heritageClauses = when {
        isClassDeclaration(parentNode) -> (parentNode as ClassDeclaration).heritageClauses?.asArray()
        isInterfaceDeclaration(parentNode) -> (parentNode as InterfaceDeclaration).heritageClauses?.asArray()
        else -> return result
    } ?: return result

    for (clause in heritageClauses) {
        for (typeExpr in clause.types.asArray()) {
            val baseType = typeChecker.getTypeAtLocation(typeExpr)
            val properties = typeChecker.getPropertiesOfType(baseType)
            for (prop in properties) {
                if (prop.name !in result) {
                    result[prop.name] = prop
                }
            }
        }
    }

    return result
}

private fun getClassTypeParameterNames(declaration: Declaration): Set<String> {
    val parent = declaration.parent ?: return emptySet()
    val typeParams = when {
        isClassDeclaration(parent) -> (parent as ClassDeclaration).typeParameters?.asArray()
        isInterfaceDeclaration(parent) -> (parent as InterfaceDeclaration).typeParameters?.asArray()
        else -> null
    }
    return typeParams?.mapNotNull { tp ->
        if (isTypeParameterDeclaration(tp)) {
            val name = (tp as TypeParameterDeclaration).name
            if (isIdentifier(name)) (name as Identifier).text else null
        } else null
    }?.toSet() ?: emptySet()
}

private fun getMethodTypeParameterNames(declaration: Declaration): Set<String> {
    val typeParams = when {
        isMethodDeclaration(declaration) -> (declaration as MethodDeclaration).typeParameters?.asArray()
        isMethodSignature(declaration) -> (declaration as MethodSignature).typeParameters?.asArray()
        else -> null
    }
    return typeParams?.mapNotNull { tp ->
        if (isTypeParameterDeclaration(tp)) {
            val name = (tp as TypeParameterDeclaration).name
            if (isIdentifier(name)) (name as Identifier).text else null
        } else null
    }?.toSet() ?: emptySet()
}

private fun isClassTypeParameterReference(
    typeNode: TypeNode?,
    baseDeclaration: Declaration,
): Boolean {
    if (typeNode == null || !isTypeReferenceNode(typeNode)) return false
    val typeRef = typeNode as TypeReferenceNode
    val typeName = typeRef.typeName
    if (!isIdentifier(typeName)) return false
    val name = (typeName as Identifier).text

    val methodTypeParams = getMethodTypeParameterNames(baseDeclaration)
    if (name in methodTypeParams) return false

    val classTypeParams = getClassTypeParameterNames(baseDeclaration)
    return name in classTypeParams
}

private sealed interface SignatureCheckResult {
    class Compatible(val baseParameterTypeNodes: Array<TypeNode?>?) : SignatureCheckResult
    class Narrowed(val narrowingInfo: NarrowingInfo) : SignatureCheckResult
    data object Incompatible : SignatureCheckResult
}

private fun checkSignature(
    node: Node,
    baseSymbol: Symbol,
    typeChecker: TypeChecker,
): SignatureCheckResult {
    val baseDeclaration = baseSymbol.valueDeclaration ?: return SignatureCheckResult.Compatible(baseParameterTypeNodes = null)

    if (isMethodDeclaration(node) && (isMethodDeclaration(baseDeclaration) || isMethodSignature(baseDeclaration))) {
        return checkMethodSignature(node, baseDeclaration, typeChecker)
    }

    if (isMethodSignature(node) && (isMethodDeclaration(baseDeclaration) || isMethodSignature(baseDeclaration))) {
        return checkMethodSignature(node, baseDeclaration, typeChecker)
    }

    if (isPropertyDeclaration(node) && (isPropertyDeclaration(baseDeclaration) || isPropertySignature(baseDeclaration))) {
        return checkPropertySignature(node, baseDeclaration, typeChecker)
    }

    if (isPropertySignature(node) && (isPropertyDeclaration(baseDeclaration) || isPropertySignature(baseDeclaration))) {
        return checkPropertySignature(node, baseDeclaration, typeChecker)
    }

    if ((isGetAccessor(node) || isSetAccessor(node))
        && (isGetAccessor(baseDeclaration) || isSetAccessor(baseDeclaration))
    ) {
        return checkAccessorSignature(node, baseDeclaration, typeChecker)
    }

    if (isMethodDeclaration(node) && (isPropertyDeclaration(baseDeclaration) || isPropertySignature(baseDeclaration))) return SignatureCheckResult.Incompatible
    if (isPropertyDeclaration(node) && (isMethodDeclaration(baseDeclaration) || isMethodSignature(baseDeclaration))) return SignatureCheckResult.Incompatible
    if (isMethodSignature(node) && (isPropertyDeclaration(baseDeclaration) || isPropertySignature(baseDeclaration))) return SignatureCheckResult.Incompatible
    if (isPropertySignature(node) && (isMethodDeclaration(baseDeclaration) || isMethodSignature(baseDeclaration))) return SignatureCheckResult.Incompatible

    return SignatureCheckResult.Compatible(baseParameterTypeNodes = null)
}

private fun checkMethodSignature(
    node: Node,
    baseDeclaration: Declaration,
    typeChecker: TypeChecker,
): SignatureCheckResult {
    val baseParams = when {
        isMethodDeclaration(baseDeclaration) -> (baseDeclaration as MethodDeclaration).parameters.asArray()
        isMethodSignature(baseDeclaration) -> (baseDeclaration as MethodSignature).parameters.asArray()
        else -> return SignatureCheckResult.Compatible(baseParameterTypeNodes = null)
    }

    val nodeParams = when {
        isMethodDeclaration(node) -> (node as MethodDeclaration).parameters.asArray()
        isMethodSignature(node) -> (node as MethodSignature).parameters.asArray()
        else -> return SignatureCheckResult.Compatible(baseParameterTypeNodes = null)
    }

    if (nodeParams.size != baseParams.size) return SignatureCheckResult.Incompatible

    val baseParameterTypeNodes = baseParams.map { it.type }.toTypedArray()

    for (i in nodeParams.indices) {
        val nodeParamType = nodeParams[i].type
        val baseParamType = baseParams[i].type

        if (nodeParamType != null && baseParamType != null) {
            if (isClassTypeParameterReference(baseParamType, baseDeclaration)) continue

            val nodeType = typeChecker.getTypeFromTypeNode(nodeParamType)
            val baseType = typeChecker.getTypeFromTypeNode(baseParamType)

            if (typeChecker.typeToString(nodeType) != typeChecker.typeToString(baseType)) {
                return SignatureCheckResult.Narrowed(NarrowingInfo(
                    baseDeclaration = baseDeclaration,
                    baseParameterTypeNodes = baseParameterTypeNodes,
                    basePropertyTypeNode = null,
                    baseIsOptional = false,
                ))
            }
        }
    }

    return SignatureCheckResult.Compatible(baseParameterTypeNodes)
}

private fun isOptionalProperty(node: Node): Boolean = when {
    isPropertyDeclaration(node) -> (node as PropertyDeclaration).questionToken != null
    isPropertySignature(node) -> (node as PropertySignature).questionToken != null
    else -> false
}

private fun checkPropertySignature(
    node: Node,
    baseDeclaration: Declaration,
    typeChecker: TypeChecker,
): SignatureCheckResult {
    val nodeTypeNode = when {
        isPropertyDeclaration(node) -> (node as PropertyDeclaration).type
        isPropertySignature(node) -> (node as PropertySignature).type
        else -> return SignatureCheckResult.Compatible(baseParameterTypeNodes = null)
    }

    val baseTypeNode = when {
        isPropertyDeclaration(baseDeclaration) -> (baseDeclaration as PropertyDeclaration).type
        isPropertySignature(baseDeclaration) -> (baseDeclaration as PropertySignature).type
        else -> return SignatureCheckResult.Compatible(baseParameterTypeNodes = null)
    }

    if (nodeTypeNode != null && baseTypeNode != null) {
        if (isClassTypeParameterReference(baseTypeNode, baseDeclaration)) return SignatureCheckResult.Compatible(baseParameterTypeNodes = null)

        val nodeType = typeChecker.getTypeFromTypeNode(nodeTypeNode)
        val baseType = typeChecker.getTypeFromTypeNode(baseTypeNode)

        if (typeChecker.typeToString(nodeType) != typeChecker.typeToString(baseType)) {
            return SignatureCheckResult.Narrowed(NarrowingInfo(
                baseDeclaration = baseDeclaration,
                baseParameterTypeNodes = emptyArray(),
                basePropertyTypeNode = baseTypeNode,
                baseIsOptional = isOptionalProperty(baseDeclaration),
            ))
        }
    }

    val nodeIsOptional = isOptionalProperty(node)
    val baseIsOptional = isOptionalProperty(baseDeclaration)

    if (nodeIsOptional != baseIsOptional) {
        return SignatureCheckResult.Narrowed(NarrowingInfo(
            baseDeclaration = baseDeclaration,
            baseParameterTypeNodes = emptyArray(),
            basePropertyTypeNode = baseTypeNode ?: nodeTypeNode,
            baseIsOptional = baseIsOptional,
        ))
    }

    return SignatureCheckResult.Compatible(baseParameterTypeNodes = null)
}

private fun checkAccessorSignature(
    node: Node,
    baseDeclaration: Declaration,
    typeChecker: TypeChecker,
): SignatureCheckResult {
    val nodeTypeNode = when {
        isGetAccessor(node) -> (node as GetAccessorDeclaration).type
        isSetAccessor(node) -> (node as SetAccessorDeclaration).parameters.asArray().getOrNull(0)?.type
        else -> return SignatureCheckResult.Compatible(baseParameterTypeNodes = null)
    }

    val baseTypeNode = when {
        isGetAccessor(baseDeclaration) -> (baseDeclaration as GetAccessorDeclaration).type
        isSetAccessor(baseDeclaration) -> (baseDeclaration as SetAccessorDeclaration).parameters.asArray().getOrNull(0)?.type
        else -> return SignatureCheckResult.Compatible(baseParameterTypeNodes = null)
    }

    if (nodeTypeNode != null && baseTypeNode != null) {
        if (isClassTypeParameterReference(baseTypeNode, baseDeclaration)) return SignatureCheckResult.Compatible(baseParameterTypeNodes = null)

        val nodeType = typeChecker.getTypeFromTypeNode(nodeTypeNode)
        val baseType = typeChecker.getTypeFromTypeNode(baseTypeNode)

        if (typeChecker.typeToString(nodeType) != typeChecker.typeToString(baseType)) {
            return SignatureCheckResult.Narrowed(NarrowingInfo(
                baseDeclaration = baseDeclaration,
                baseParameterTypeNodes = emptyArray(),
                basePropertyTypeNode = baseTypeNode,
                baseIsOptional = false,
            ))
        }
    }

    return SignatureCheckResult.Compatible(baseParameterTypeNodes = null)
}

val detectOverrideModifier: InheritanceModifier = { node, context ->
    val memberName = getMemberName(node)

    val typeScriptService = context.lookupService(typeScriptServiceKey)
    val typeChecker = typeScriptService?.program?.getTypeChecker()

    val parentNode = typeScriptService?.getParent(node)

    if (
        memberName != null
        && !isStaticMember(node)
        && typeChecker != null
        && parentNode != null
        && (isClassDeclaration(parentNode) || isInterfaceDeclaration(parentNode))
    ) {
        val baseSymbols = collectBaseSymbols(parentNode, typeChecker)
        val baseSymbol = baseSymbols[memberName]

        if (baseSymbol != null) {
            when (val result = checkSignature(node, baseSymbol, typeChecker)) {
                is SignatureCheckResult.Compatible -> {
                    if (result.baseParameterTypeNodes != null) {
                        val overrideDetectionService = context.lookupService(overrideDetectionServiceKey)
                        overrideDetectionService?.registerCompatibleOverride(node, CompatibleOverrideInfo(result.baseParameterTypeNodes))
                    }
                    "override"
                }
                is SignatureCheckResult.Narrowed -> {
                    val overrideDetectionService = context.lookupService(overrideDetectionServiceKey)
                    overrideDetectionService?.registerNarrowing(node, result.narrowingInfo)
                    null
                }
                is SignatureCheckResult.Incompatible -> null
            }
        } else {
            null
        }
    } else {
        null
    }
}

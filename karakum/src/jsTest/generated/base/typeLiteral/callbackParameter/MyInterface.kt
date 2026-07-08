// Automatically generated - do not modify!

@file:JsModule("sandbox-base/typeLiteral/callbackParameter")
@file:JsNonModule

package sandbox.base.typeLiteral.callbackParameter

external interface MyInterface {
var conflictHandler: ((conflictType: MyInterfaceConflictHandlerConflictType) -> Boolean)?
fun method(cb: (options: MyInterfaceMethodCbOptions) -> Unit): String
}

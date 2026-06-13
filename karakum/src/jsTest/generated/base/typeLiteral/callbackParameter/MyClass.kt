// Automatically generated - do not modify!

@file:JsModule("sandbox-base/typeLiteral/callbackParameter")
@file:JsNonModule

package sandbox.base.typeLiteral.callbackParameter

open external class MyClass {
open var conflictHandler: ((conflictType: MyClassConflictHandlerConflictType) -> Boolean)?
open fun method(cb: (options: MyClassMethodCbOptions) -> Unit): String
}

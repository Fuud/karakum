// Automatically generated - do not modify!

@file:JsModule("sandbox-base/interface/generic")
@file:JsNonModule

package sandbox.base.`interface`.generic

external interface GenericInterface<T, U : ExampleBoundInterface> {
var firstField: T
var secondField: Double
fun firstMethod(firstParam: String, secondParam: U): Unit
}

// Automatically generated - do not modify!

@file:JsModule("sandbox-override/type-narrowing")

package sandbox.override.typeNarrowing

external class NarrowingEmitter : IEventEmitter {
override var onData: (data: Any?) -> Unit
@JsName("onData")
var onDataNarrowed: (data: js.buffer.ArrayBuffer) -> Unit
}

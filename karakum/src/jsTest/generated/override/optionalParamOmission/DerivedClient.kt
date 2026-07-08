// Automatically generated - do not modify!

@file:JsModule("sandbox-override/optional-param-omission")
@file:JsNonModule

package sandbox.override.optionalParamOmission

open external class DerivedClient : BaseClient {
override fun connect(host: String, port: Double, timeout: Double): Unit
open fun connect(host: String): Unit
}

// Automatically generated - do not modify!

@file:JsModule("sandbox-override/method-narrowing-optional")
@file:JsNonModule

package sandbox.override.methodNarrowingOptional

open external class Logger : BaseLogger {
override fun log(name: String, value: String, immediately: Boolean): Unit
open fun log(name: StatLog, value: String = definedExternally, immediately: Boolean = definedExternally): Unit
}

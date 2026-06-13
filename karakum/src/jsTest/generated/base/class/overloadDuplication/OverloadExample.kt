// Automatically generated - do not modify!

@file:JsModule("sandbox-base/class/overloadDuplication")
@file:JsNonModule

package sandbox.base.`class`.overloadDuplication

open external class OverloadExample {
open fun cacheExternalId(id: String, externalId: String): Unit

open fun cacheExternalId(id: InternalUserId, externalId: String): Unit
}

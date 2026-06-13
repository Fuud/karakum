// Automatically generated - do not modify!

package extension.promiseMethods

import extension.promiseFunctions.CustomPromise

open external class ClassWithPromiseMethods {
@JsName("returnsPromise1")
open fun returnsPromise1Async(): js.promise.Promise<String>

@seskar.js.JsAsync
open suspend fun returnsPromise1(): String
@JsName("returnsPromise2")
open fun returnsPromise2Async(param: String): js.promise.Promise<Boolean>

@seskar.js.JsAsync
open suspend fun returnsPromise2(param: String): Boolean
@JsName("returnsPromise2")
open fun returnsPromise2Async(param: Boolean): js.promise.Promise<Boolean>

@seskar.js.JsAsync
open suspend fun returnsPromise2(param: Boolean): Boolean
/* should be excluded */


/* should be excluded */




/* should be excluded */

@JsName("returns-promise-3")
open fun returnsPromise3Async(): js.promise.Promise<Boolean>

@seskar.js.JsAsync
open suspend fun returnsPromise3(): Boolean
open fun returnsPromiseIgnored(): js.promise.Promise<String>
@JsName("returnsCustomPromise")
open fun returnsCustomPromiseAsync(): CustomPromise

@seskar.js.JsAsync
open suspend fun returnsCustomPromise(): Any?
}

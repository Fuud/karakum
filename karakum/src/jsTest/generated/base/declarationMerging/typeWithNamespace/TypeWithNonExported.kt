// Automatically generated - do not modify!

package sandbox.base.declarationMerging.typeWithNamespace

external interface TypeWithNonExported : js.objects.ReadonlyRecord<String, Any?> {
interface Exported {
var x: Double
}
interface NonExported : TypeWithNonExported {
var y: String
}
companion object {
val num: Double
}
}

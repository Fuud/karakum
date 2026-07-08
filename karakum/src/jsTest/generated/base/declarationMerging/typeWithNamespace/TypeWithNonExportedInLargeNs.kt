// Automatically generated - do not modify!

package sandbox.base.declarationMerging.typeWithNamespace

external interface TypeWithNonExportedInLargeNs : js.objects.ReadonlyRecord<String, Any?> {
interface ExportedA {
var a: Double
}
interface ExportedB {
var b: Double
}
interface ExportedC {
var c: Double
}
interface Base : TypeWithNonExportedInLargeNs {
var type: String
}
interface DerivedA : TypeWithNonExportedInLargeNs.Base {
var a: Double
}
interface DerivedB : TypeWithNonExportedInLargeNs.Base {
var b: Double
}
}

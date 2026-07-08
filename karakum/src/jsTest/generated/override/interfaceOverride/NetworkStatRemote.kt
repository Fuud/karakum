// Automatically generated - do not modify!

@file:JsModule("sandbox-override/interface-override")
@file:JsNonModule

package sandbox.override.interfaceOverride

external interface NetworkStatRemote : NetworkStat {
override var rtt: Any /* WeightedAverage | number */
@JsName("rtt")
var rttNarrowed: Double
override var loss: Any /* WeightedAverage | number */
@JsName("loss")
var lossNarrowed: Double
}

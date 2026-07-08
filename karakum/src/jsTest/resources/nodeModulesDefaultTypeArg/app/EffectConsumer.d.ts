import { Effect } from "sandbox-dep"

// Scenario A+B: Reference Effect without angle brackets — default type arguments
// (DrawParams, RenderingContext) should be imported in generated Kotlin.
export interface EffectConsumer {
    effect: Effect | null
}

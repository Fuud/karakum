import { LocalEffect } from "./types"

// Scenario C: Reference LocalEffect without angle brackets — default type argument
// LocalDrawParams should be rendered. Since both are in the app source (not node_modules),
// this should work correctly.
export interface LocalEffectConsumer {
    effect: LocalEffect
}

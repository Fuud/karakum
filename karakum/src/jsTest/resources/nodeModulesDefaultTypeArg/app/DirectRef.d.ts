import type { DrawParams } from "sandbox-dep"

// Scenario E: Direct reference to DrawParams from a single-package-mapped module.
// This should produce a correct individual import.
export interface DirectRef {
    params: DrawParams
}

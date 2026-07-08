// Scenario C: Local types (not from node_modules) with default type arguments.
// This is the baseline that should work regardless of the bug.

export interface LocalDrawParams {
    timestamp: number
    duration?: number
}

export abstract class LocalEffect<T extends LocalDrawParams = LocalDrawParams> {
    abstract draw(params: T): void
}

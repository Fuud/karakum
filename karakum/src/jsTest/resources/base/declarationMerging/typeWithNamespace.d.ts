type TypeWithNamespace = Record<string, any>

namespace TypeWithNamespace {
    export interface NestedType {
        field: string
    }
    export const value: number
}

type TypeWithDeclareNamespace = Record<string, any>

declare namespace TypeWithDeclareNamespace {
    export interface NestedType {
        field: string
    }
    export const value: number
}

type TypeWithNonExported = Record<string, any>

declare namespace TypeWithNonExported {
    export interface Exported { x: number }
    interface NonExported extends TypeWithNonExported { y: string }
    export const num: number
}

type TypeWithNonExportedInLargeNs = Record<string, any>

export interface ExternalRef extends TypeWithNonExportedInLargeNs { ref: string }

declare namespace TypeWithNonExportedInLargeNs {
    export interface ExportedA { a: number }
    export interface ExportedB { b: number }
    export interface ExportedC { c: number }
    interface Base extends TypeWithNonExportedInLargeNs { type: string }
    export interface DerivedA extends Base { a: number }
    export interface DerivedB extends Base { b: number }
}


interface InterfaceWithVariable {
    a: number
}

declare const InterfaceWithVariable: InterfaceWithVariable

interface InterfaceWithNamespace {
    b: string
}

namespace InterfaceWithNamespace {
    export const c: number
}

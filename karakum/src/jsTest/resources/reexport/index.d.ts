declare module "reexport-root" {
    interface RootInterface {
        value: string
    }

    export { SubInterface, SubClass } from "reexport-sub"
    export { SubType as RenamedType } from "reexport-sub"
    export * from "reexport-sub"
}

declare module "reexport-root" {
    export { RootInterface } from "reexport-root"
    export { SeparateFileClass, SeparateFileInterface } from "reexport-separatefile"
    export { GenericInterface, GenericClass as AliasedGenericClass, GenericType } from "reexport-sub"
}

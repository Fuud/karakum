interface Config {
    name: string
    version: number
    debug: boolean
    metadata: object
}

type Primitive = string | number | boolean

type PrimitiveKeys<T> = {
    [K in keyof T]-?: Exclude<T[K], undefined> extends Primitive ? K : never
}[keyof T]

export type PrimitiveConfig = Pick<Config, PrimitiveKeys<Config>>

export declare function getPrimitiveConfigKeys(): PrimitiveKeys<Config>

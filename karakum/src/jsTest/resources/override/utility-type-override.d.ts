interface Options {
    a: boolean
    b: number
}

export declare abstract class Base {
    abstract init(opts: Partial<Options>): Promise<void>
}

export declare class Derived extends Base {
    init(opts?: Partial<Options>): Promise<void>
}

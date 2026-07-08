declare module "reexport-sub" {
    interface SubInterface {
        value: string
    }

    class SubClass {
        method(): void
    }

    type SubType = string | number

    enum SubEnum {
        A,
        B,
    }

    const subValue: number

    function subFunction(): void

    interface GenericInterface<T> {
        value: T
    }

    class GenericClass<T, U> {
        method(arg: T): U
    }

    type GenericType<V> = Array<V>
}

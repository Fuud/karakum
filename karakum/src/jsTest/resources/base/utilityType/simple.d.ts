interface Foo {
    name: string
    age: number
}

interface Bar {
    name?: string
    age?: number
}

declare function processPartial(opts: Partial<Foo>): void
declare function processRequired(opts: Required<Bar>): void
declare function processPick(opts: Pick<Foo, "name">): void
declare function processOmit(opts: Omit<Foo, "age">): void
declare function processReadonly(opts: Readonly<Foo>): void

type MaybeString = string | null | undefined
declare function processNonNullable(value: NonNullable<MaybeString>): void

declare class MyClass {
    method(opts: Pick<Foo, "name">): void
}

interface Person {
    name: string
    age: number
    email: string
}

interface PersonWithoutEmail extends Omit<Person, "email"> {
}

interface RequiredPerson extends Required<Partial<Person>> {
}

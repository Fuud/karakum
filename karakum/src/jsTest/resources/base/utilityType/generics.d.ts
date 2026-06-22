interface Foo { name: string; age: number }

export type PickKey<T, K extends keyof T> = Extract<keyof T, K>
export type ExcludeKey<T, K extends keyof T> = Exclude<keyof T, K>
export type FooKeys = keyof Foo

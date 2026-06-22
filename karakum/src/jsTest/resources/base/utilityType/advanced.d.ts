// ReturnType: extract return type of a function
interface Point { x: number; y: number }
declare function getPoint(): Point
type PointResult = ReturnType<typeof getPoint>
declare function usePoint(p: PointResult): void

// ReturnType of a function with complex return type
declare function getItems(): string[]
type ItemsResult = ReturnType<typeof getItems>
declare function useItems(items: ItemsResult): void

// Parameters: extract parameter types as a tuple
declare function greet(name: string, age: number): void
type GreetParams = Parameters<typeof greet>
declare function forwardGreet(args: GreetParams): void

// InstanceType: extract instance type of a constructor
declare class Console { log(msg: string): void }
type ConsoleInstance = InstanceType<typeof Console>
declare function useConsole(instance: ConsoleInstance): void

// ReturnType with generic function
declare function identity<T>(value: T): T
type IdentityResult = ReturnType<typeof identity>

// Class with default type parameters — the core case
export declare abstract class Container<T = string, U = number> {
}

// Reference without type arguments — should get defaults
export declare class DefaultRef extends Container {
    constructor(value: Container);
    items: Container[];
    current: Container;
    getWrapped(): Container<string, number>;
}

// Reference with one explicit type argument — should fill remaining defaults
export declare class PartialRef extends Container<boolean> {
    constructor(value: Container<boolean>);
}

// Reference with all explicit type arguments — no defaults needed
export declare class FullRef extends Container<boolean, string> {
}

// Interface with default type parameters
export interface Observer<T = string> {
    next(value: T): void;
}

// Reference in property, parameter, and return types
export declare class ObserverUser {
    observer: Observer;
    subscribe(obs: Observer): void;
    create(): Observer;
}

// Type alias with default type parameters
export type Result<T = string> = {
    value: T;
};

// Function using default type ref
export declare function processResult(result: Result): void;

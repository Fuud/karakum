export interface IHandler {
    handle(data: unknown): void;
}

export declare class NarrowingHandler implements IHandler {
    handle(data: ArrayBuffer): void;
}

export interface IEventEmitter {
    onData: (data: unknown) => void;
}

export declare class NarrowingEmitter implements IEventEmitter {
    onData: (data: ArrayBuffer) => void;
}

export interface IConnector {
    connect(host: string, port?: number): void;
}

export declare class SimpleConnector implements IConnector {
    connect(host: string): void;
}

export declare abstract class BaseClient {
    abstract connect(host: string, port?: number, timeout?: number): void;
}

export declare class DerivedClient extends BaseClient {
    connect(host: string): void;
}

export interface IEffect {
    draw(source: string): void;
    name: string;
}

export declare class ConcreteEffect implements IEffect {
    draw(source: string): void;
    name: string;
    customMethod(): void;
}

export interface IProcessor {
    process(data: string): void;
}

export declare class ProcessorImpl implements IProcessor {
    process(data: string): void;
}

export declare abstract class AbstractBase {
    abstract render(): void;
    abstract get label(): string;
}

export declare class ConcreteImpl extends AbstractBase {
    render(): void;
    get label(): string;
    ownMethod(): void;
}

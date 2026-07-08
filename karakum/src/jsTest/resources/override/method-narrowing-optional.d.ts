export abstract class BaseLogger {
    log(name: string, value?: string, immediately?: boolean): void;
}

export declare class Logger extends BaseLogger {
    log(name: StatLog, value?: string, immediately?: boolean): void;
}

export enum StatLog { A, B }

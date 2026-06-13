export interface IConfig {
    configure(name: string, options?: { verbose?: boolean; timeout?: number }): void;
}

export declare class ConfigService implements IConfig {
    configure(name: string, { verbose, timeout }?: { verbose?: boolean; timeout?: number }): void;
}

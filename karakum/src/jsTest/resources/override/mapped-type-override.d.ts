export interface IOptions {
    apply(changes: { [key in OptionType]?: boolean }): void;
}

export declare class OptionHandler implements IOptions {
    apply(changes: { [key in OptionType]?: boolean }): void;
}

declare enum OptionType {
    VERBOSE = "VERBOSE",
    TIMEOUT = "TIMEOUT",
}

export declare abstract class BaseProcessor {
    abstract process(data: string, config?: { [key in SettingType]?: number }): void;
}

declare enum SettingType {
    RETRIES = "RETRIES",
    DELAY = "DELAY",
}

export declare class DerivedProcessor extends BaseProcessor {
    process(data: string, config?: { [key in SettingType]?: number }): void;
}

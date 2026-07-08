import { IBase } from "./base"

export declare class Derived implements IBase {
    configure(name: string, options?: { verbose?: boolean; timeout?: number }): void;
}

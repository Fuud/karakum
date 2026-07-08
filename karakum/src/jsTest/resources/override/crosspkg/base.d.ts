export interface IBase {
    configure(name: string, options?: { verbose?: boolean; timeout?: number }): void;
}

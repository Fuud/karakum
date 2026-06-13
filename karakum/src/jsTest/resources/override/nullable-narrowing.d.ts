export interface OptionalProperties {
    value?: string;
    count?: number;
    tag: string;
}

export interface NarrowedProperties extends OptionalProperties {
    value: string;
    count: number;
}

interface Config {
    width: number
    height: number
    title: string
    color: string
}

// Partial / Required / Readonly — single-arg utility types
declare function usePartial(opts: Partial<Config>): void
declare function useRequired(opts: Required<Config>): void
declare function useReadonly(opts: Readonly<Config>): void

// Pick / Omit — with keys
declare function usePick(opts: Pick<Config, "title">): void
declare function usePickMultiple(opts: Pick<Config, "title" | "width">): void
declare function useOmit(opts: Omit<Config, "color">): void
declare function useOmitMultiple(opts: Omit<Config, "color" | "height">): void

// Heritage clause — currently produces Temp0/Temp1
interface ConfigWithoutColor extends Omit<Config, "color"> {
}

// Nested utility type
interface StrictConfig extends Required<Partial<Config>> {
}

export namespace Wav2lip {
    export interface ConfigInput {
        key: string
        value: string
    }

    export interface Config {
        input: ConfigInput
    }

    export type Effect = "blur" | "sharpen"
}

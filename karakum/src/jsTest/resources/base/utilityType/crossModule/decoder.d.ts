export interface DecoderOptions {
    useFrameBatching: boolean;
    useLowResolution: boolean;
    fpsLimit: number;
    specificVersion: string | null;
}

import { DecoderOptions } from './decoder';

export interface RenderingOptions {
    useAI: boolean;
    maxPoolSize: number | null;
    decoderOptions: Partial<DecoderOptions>;
}

export interface StrictRenderingOptions {
    decoderOptions: NonNullable<DecoderOptions>;
}

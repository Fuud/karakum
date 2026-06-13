export type AnimojiVersion = 1 | 2;

export interface VmojiOptions {
    protocolVersion: AnimojiVersion;
    renderingOptions: RenderingOptionsPartial;
}

export interface RenderingOptionsPartial {
    useAI?: boolean;
    useImageBitmap?: boolean;
}

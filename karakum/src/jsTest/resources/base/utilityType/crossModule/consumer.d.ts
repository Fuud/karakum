import type * as Vmoji from './vmoji';

export interface ParamsObject {
    vmojiOptions: {
        protocolVersion: Vmoji.AnimojiVersion;
        renderingOptions: Partial<Vmoji.RenderingOptionsPartial>;
    } | null;
}

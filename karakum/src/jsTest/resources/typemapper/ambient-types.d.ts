interface MyConsumer {
    readonly source: CanvasImageSourceWebCodecs
    callback: VideoFrameCallback
}

interface CanvasImageSourceWebCodecs {
    readonly data: string
}

interface VideoFrameCallback {
    now: number
}

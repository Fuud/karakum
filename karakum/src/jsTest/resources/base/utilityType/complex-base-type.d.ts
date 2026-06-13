interface Callbacks {
    onData: (data: unknown) => void
    onError: (error: string) => void
}

// NonNullable with named type
declare function useNonNullable(value: NonNullable<Callbacks>): void

// NonNullable with typeof — base type is anonymous
export declare let SDK: {
    debugMessage: (msg: string) => void
    browser: string
    DebugMessageType: number
} | null
export declare function setSDK(sdk: NonNullable<typeof SDK>): void

// NonNullable with indexed access — resolves to named type
interface ParamsObject {
    vmojiOptions: Callbacks | null
    timeout: number
}
declare function getVmojiOptions(): NonNullable<ParamsObject['vmojiOptions']>

// NonNullable with indexed access — resolves to anonymous type
interface Settings {
    handler: { onEvent: (name: string) => void; priority: number } | null
}
declare function getHandler(): NonNullable<Settings['handler']>

// Pick with many keys — name must be truncated with hash
interface Report {
    rtt: number
    jitter_video: number
    jitter_audio: number
    interframe_delay_variance: number
    freeze_count: number
    total_freezes_duration: number
    ss_freeze_count: number
    ss_total_freezes_duration: number
    inserted_audio_samples_for_deceleration: number
    removed_audio_samples_for_acceleration: number
    concealed_audio_samples: number
    total_audio_energy: number
}
declare function useReportPick(opts: Pick<Report, "rtt" | "jitter_video" | "jitter_audio" | "interframe_delay_variance" | "freeze_count" | "total_freezes_duration" | "ss_freeze_count" | "ss_total_freezes_duration" | "inserted_audio_samples_for_deceleration" | "removed_audio_samples_for_acceleration" | "concealed_audio_samples" | "total_audio_energy">): void

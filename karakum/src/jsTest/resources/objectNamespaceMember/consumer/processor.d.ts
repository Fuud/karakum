import { Wav2lip, ConfigInput, Config, Effect } from "../provider/wav2lip"

export interface ProcessorConfig {
    input: Wav2lip.ConfigInput
    config: Wav2lip.Config
    effect: Wav2lip.Effect
}

import { RemoteConfig } from "./provider"

// QualifiedName: RemoteConfig.Entry should import RemoteConfig namespace object
export interface ConfigRef {
    entry: RemoteConfig.Entry
}

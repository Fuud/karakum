declare module "default-consumer" {
    import ConversationOption from "default-provider"
    import * as Provider from "default-provider"
    import Status from "other-default-provider"

    const option: ConversationOption
    const provider: typeof Provider
    const status: Status
}

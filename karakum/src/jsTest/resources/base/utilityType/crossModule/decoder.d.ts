interface DecoderOptions {
    encoding: string
    fatal: boolean
}

interface ErrorType {
    code: number
    message: string
}

interface DecoderConfig {
    options: DecoderOptions
    errorType: ErrorType | null
    name: string
}

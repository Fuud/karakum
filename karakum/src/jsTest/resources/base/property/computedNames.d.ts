export const enum TransportType {
    WEBSOCKET = "ws",
    WEBTRANSPORT = "wt",
}

export interface TransportStats {
    [TransportType.WEBSOCKET]: string
    [TransportType.WEBTRANSPORT]: number
}

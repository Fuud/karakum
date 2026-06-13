export interface BaseInterface {
    field: string;
    method(data: string): void;
}

export interface DerivedInterface extends BaseInterface {
    field: string;
    method(data: string): void;
}

export interface NetworkStat {
    rtt: WeightedAverage | number;
    loss: WeightedAverage | number;
    bitrate: number;
}

export interface NetworkStatRemote extends NetworkStat {
    rtt: number;
    loss: number;
}

interface WeightedAverage {
    value: number;
}

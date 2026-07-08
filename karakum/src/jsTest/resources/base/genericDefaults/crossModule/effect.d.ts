import { DrawParams } from "./params"

export interface Effect<T = DrawParams> {
    draw(params: T): void
}

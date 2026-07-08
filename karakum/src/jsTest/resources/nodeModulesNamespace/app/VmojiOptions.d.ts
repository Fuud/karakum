import { Vmoji } from "sandbox-vmoji"

// Partial<typeof Vmoji> expands to a namespace type.
// Property walking should be skipped for namespace types to avoid
// dead individual imports of namespace members (e.g. AnimojiVersion).
export interface VmojiOptions {
    vmoji: Partial<typeof Vmoji>
}

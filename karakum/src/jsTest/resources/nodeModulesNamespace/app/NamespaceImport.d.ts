import type * as Vmoji from "sandbox-vmoji"

// Namespace import (import type * as X) from node_modules —
// the Vmoji namespace object must be imported, not individual members.
export interface NamespaceImportConfig {
    version: Vmoji.AnimojiVersion
}

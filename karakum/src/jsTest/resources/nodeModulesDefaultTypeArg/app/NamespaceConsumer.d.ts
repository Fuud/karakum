import { Vmoji } from "sandbox-vmoji"

// Scenario D: Namespace member accessed via QualifiedName — should NOT be imported
// individually (the namespace object import is sufficient).
export interface NamespaceConsumer {
    version: Vmoji.AnimojiVersion
}

package io.github.sgrishchenko.karakum.nodeModulesImportGathering

import io.github.sgrishchenko.karakum.configuration.InputResolutionStrategy
import io.github.sgrishchenko.karakum.configuration.plain
import io.github.sgrishchenko.karakum.generate
import io.github.sgrishchenko.karakum.loadTestConfig
import io.github.sgrishchenko.karakum.util.ruleOf
import kotlinx.coroutines.test.runTest
import node.buffer.BufferEncoding
import node.buffer.utf8
import node.fs.*
import node.path.path
import kotlin.test.Test
import kotlin.test.assertTrue

class NodeModulesImportGatheringTest {
    @Test
    fun test() = runTest {
        val testConfig = loadTestConfig()
        val cwd = path.resolve(testConfig.lib, "kotlin", "nodeModulesImportGathering")
        val outputDir = path.resolve(testConfig.output, "nodeModulesImportGathering")

        generate {
            this.cwd = cwd
            inputResolutionStrategy = InputResolutionStrategy.plain
            input = listOf("app/**/*.d.ts")
            output = outputDir
            libraryName = "sandbox-nodeModulesImportGathering"
            libraryNameOutputPrefix = false
            isolatedOutputPackage = true
            moduleNameMapper = mapOf()
            importMapper = mapOf(
                "^sandbox-vmoji$" to ruleOf("sandbox.vmoji"),
            )
        }

        // Parameters<typeof Vmoji.setSDK>[0] resolves to the anonymous type:
        // { callback: Callback; DebugMessageType: typeof DebugMessageType }
        // This becomes a Temp* interface. The imports for types used inside
        // the anonymous type literal (Callback, DebugMessageType) must be
        // present in the generated file.
        val tempKt = path.join(outputDir, "Consumer", "Temp0.kt")
        assertTrue(existsSync(tempKt), "Expected file $tempKt to exist")

        val content = readFile(tempKt, BufferEncoding.utf8).replace("\r\n", "\n")

        // Callback is declared in sandbox-vmoji/types/index.d.ts.
        // Auto-resolution should compute sandbox.vmoji + types = sandbox.vmoji.types.
        // The import must be present because the Temp* interface has `var callback: Callback`.
        assertTrue(
            "import sandbox.vmoji.types.Callback" in content,
            "Expected import 'sandbox.vmoji.types.Callback' for type inside synthetic type literal, but not found in:\n$content"
        )

        // DebugMessageType is declared in sandbox-vmoji/types/index.d.ts.
        // Referenced as `typeof DebugMessageType` inside the anonymous type literal.
        assertTrue(
            "import sandbox.vmoji.types.DebugMessageType" in content,
            "Expected import 'sandbox.vmoji.types.DebugMessageType' for type inside synthetic type literal, but not found in:\n$content"
        )
    }
}

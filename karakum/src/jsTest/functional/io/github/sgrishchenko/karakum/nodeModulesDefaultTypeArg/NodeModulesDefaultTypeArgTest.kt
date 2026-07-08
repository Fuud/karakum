package io.github.sgrishchenko.karakum.nodeModulesDefaultTypeArg

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
import kotlin.test.assertFalse

class NodeModulesDefaultTypeArgTest {
    @Test
    fun test() = runTest {
        val testConfig = loadTestConfig()
        val cwd = path.resolve(testConfig.lib, "kotlin", "nodeModulesDefaultTypeArg")
        val outputDir = path.resolve(testConfig.output, "nodeModulesDefaultTypeArg")

        generate {
            this.cwd = cwd
            inputResolutionStrategy = InputResolutionStrategy.plain
            input = listOf("app/**/*.d.ts")
            output = outputDir
            libraryName = "sandbox-nodeModulesDefaultTypeArg"
            libraryNameOutputPrefix = false
            isolatedOutputPackage = true
            moduleNameMapper = mapOf()
            importMapper = mapOf(
                "^sandbox-dep$" to ruleOf("sandbox.dep"),
                "^sandbox-vmoji$" to ruleOf("sandbox.vmoji"),
            )
        }

        // Scenario A: Default type argument import from single-package-mapped module.
        // Effect<T extends DrawParams = DrawParams, K extends RenderingContext = RenderingContext>
        // is referenced without angle brackets. DrawParams should be imported.
        val effectConsumerKt = path.join(outputDir, "EffectConsumer", "EffectConsumer.kt")
        assertTrue(existsSync(effectConsumerKt), "Expected file $effectConsumerKt to exist")
        val effectConsumerContent = readFile(effectConsumerKt, BufferEncoding.utf8).replace("\r\n", "\n")
        assertTrue(
            "import sandbox.dep.effects.DrawParams" in effectConsumerContent,
            "Expected default type argument import 'sandbox.dep.effects.DrawParams' in:\n$effectConsumerContent"
        )

        // Scenario B: Multiple default type arguments — RenderingContext should also be imported.
        assertTrue(
            "import sandbox.dep.rendering.RenderingContext" in effectConsumerContent,
            "Expected default type argument import 'sandbox.dep.rendering.RenderingContext' in:\n$effectConsumerContent"
        )

        // Scenario C: Default type arg for non-node_modules types (baseline).
        // LocalEffect<T extends LocalDrawParams = LocalDrawParams> is in app/types.d.ts,
        // referenced from app/consumer.d.ts. LocalDrawParams should be imported.
        val localConsumerKt = path.join(outputDir, "consumer", "LocalEffectConsumer.kt")
        assertTrue(existsSync(localConsumerKt), "Expected file $localConsumerKt to exist")
        val localConsumerContent = readFile(localConsumerKt, BufferEncoding.utf8).replace("\r\n", "\n")
        assertTrue(
            "import sandbox.nodeModulesDefaultTypeArg.types.LocalDrawParams" in localConsumerContent,
            "Expected default type argument import for LocalDrawParams in:\n$localConsumerContent"
        )

        // Scenario D: Namespace member accessed via QualifiedName should NOT be imported individually.
        // This is a regression guard — the fix must not break namespace member handling.
        val namespaceConsumerKt = path.join(outputDir, "NamespaceConsumer", "NamespaceConsumer.kt")
        assertTrue(existsSync(namespaceConsumerKt), "Expected file $namespaceConsumerKt to exist")
        val namespaceConsumerContent = readFile(namespaceConsumerKt, BufferEncoding.utf8).replace("\r\n", "\n")
        assertFalse(
            "import sandbox.vmoji.AnimojiVersion" in namespaceConsumerContent,
            "Namespace member AnimojiVersion should NOT be imported individually, but found in:\n$namespaceConsumerContent"
        )

        // Scenario E: Direct reference to type from single-package-mapped module works.
        // DrawParams imported directly (not as a default type arg) should produce correct import.
        val directRefKt = path.join(outputDir, "DirectRef", "DirectRef.kt")
        assertTrue(existsSync(directRefKt), "Expected file $directRefKt to exist")
        val directRefContent = readFile(directRefKt, BufferEncoding.utf8).replace("\r\n", "\n")
        assertTrue(
            "import sandbox.dep.effects.DrawParams" in directRefContent,
            "Expected direct import 'sandbox.dep.effects.DrawParams' in:\n$directRefContent"
        )
    }
}

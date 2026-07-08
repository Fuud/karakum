package io.github.sgrishchenko.karakum.nodeModulesImportAuto

import io.github.sgrishchenko.karakum.configuration.InputResolutionStrategy
import io.github.sgrishchenko.karakum.configuration.plain
import io.github.sgrishchenko.karakum.generate
import io.github.sgrishchenko.karakum.loadTestConfig
import io.github.sgrishchenko.karakum.util.ruleOf
import js.objects.Object
import kotlinx.coroutines.test.runTest
import node.buffer.BufferEncoding
import node.buffer.utf8
import node.fs.*
import node.path.path
import kotlin.test.Test
import kotlin.test.assertTrue

class NodeModulesImportAutoTest {
    @Test
    fun test() = runTest {
        val testConfig = loadTestConfig()
        val cwd = path.resolve(testConfig.lib, "kotlin", "nodeModulesImportAuto")
        val outputDir = path.resolve(testConfig.output, "nodeModulesImportAuto")

        generate {
            this.cwd = cwd
            inputResolutionStrategy = InputResolutionStrategy.plain
            input = listOf("app/**/*.d.ts")
            output = outputDir
            libraryName = "sandbox-nodeModulesImportAuto"
            libraryNameOutputPrefix = false
            isolatedOutputPackage = true
            moduleNameMapper = mapOf()
            importMapper = mapOf(
                "^sandbox-dep$" to ruleOf("sandbox.dep"),
            )
        }

        val serviceKt = path.join(outputDir, "Service", "Service.kt")
        assertTrue(existsSync(serviceKt), "Expected file $serviceKt to exist")

        val content = readFile(serviceKt, BufferEncoding.utf8).replace("\r\n", "\n")

        // DrawParams is declared in effects/ subdirectory of sandbox-dep package.
        // The importMapper string rule "sandbox.dep" is the base package.
        // Auto-resolution should compute sandbox.dep + effects = sandbox.dep.effects
        assertTrue(
            "import sandbox.dep.effects.DrawParams" in content,
            "Expected auto-resolved import 'sandbox.dep.effects.DrawParams' in:\n$content"
        )
    }
}

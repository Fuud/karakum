package io.github.sgrishchenko.karakum.objectNamespaceMember

import io.github.sgrishchenko.karakum.configuration.InputResolutionStrategy
import io.github.sgrishchenko.karakum.configuration.plain
import io.github.sgrishchenko.karakum.generate
import io.github.sgrishchenko.karakum.loadTestConfig
import kotlinx.coroutines.test.runTest
import node.buffer.BufferEncoding
import node.buffer.utf8
import node.fs.*
import node.path.path
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class ObjectNamespaceMemberTest {
    @Test
    fun test() = runTest {
        val testConfig = loadTestConfig()
        val cwd = path.resolve(testConfig.lib, "kotlin", "objectNamespaceMember")
        val outputDir = path.resolve(testConfig.output, "objectNamespaceMember")

        generate {
            this.cwd = cwd
            inputResolutionStrategy = InputResolutionStrategy.plain
            input = listOf("**/*.d.ts")
            output = outputDir
            libraryName = "sandbox-objectNamespaceMember"
            libraryNameOutputPrefix = false
            isolatedOutputPackage = true
        }

        val processorConfigKt = path.join(outputDir, "consumer", "processor", "ProcessorConfig.kt")
        assertTrue(existsSync(processorConfigKt), "Expected file $processorConfigKt to exist")

        val content = readFile(processorConfigKt, BufferEncoding.utf8).replace("\r\n", "\n")

        // Object-namespace members accessed via QualifiedName (e.g. Wav2lip.ConfigInput)
        // should NOT be imported individually. The namespace import is sufficient
        // because the code uses the qualified form (Wav2lip.ConfigInput).
        assertFalse(
            "import sandbox.objectNamespaceMember.provider.wav2lip.ConfigInput" in content,
            "Object-namespace member ConfigInput should NOT be imported individually, but found in:\n$content"
        )
        assertFalse(
            "import sandbox.objectNamespaceMember.provider.wav2lip.Config" in content,
            "Object-namespace member Config should NOT be imported individually, but found in:\n$content"
        )
        assertFalse(
            "import sandbox.objectNamespaceMember.provider.wav2lip.Effect" in content,
            "Object-namespace member Effect should NOT be imported individually, but found in:\n$content"
        )

        // The namespace object import SHOULD be present
        assertTrue(
            "import sandbox.objectNamespaceMember.provider.wav2lip.Wav2lip" in content,
            "Object-namespace Wav2lip should be imported, but not found in:\n$content"
        )
    }
}

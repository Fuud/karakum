package io.github.sgrishchenko.karakum.reexport

import io.github.sgrishchenko.karakum.configuration.NamespaceStrategy
import io.github.sgrishchenko.karakum.configuration.`package`
import io.github.sgrishchenko.karakum.generateTests
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ReexportTest {
    @Test
    fun test() = runTest {
        generateTests("reexport") { testOutput ->
            input = listOf("**/*.d.ts")
            output = testOutput
            libraryName = "sandbox-reexport"
            namespaceStrategy = mapOf(
                "reexport-sub" to NamespaceStrategy.`package`,
                "reexport-root" to NamespaceStrategy.`package`,
                "reexport-separatefile" to NamespaceStrategy.`package`,
            )
        }
    }
}

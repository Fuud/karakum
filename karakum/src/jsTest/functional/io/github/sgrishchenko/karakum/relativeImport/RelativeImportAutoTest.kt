package io.github.sgrishchenko.karakum.relativeImport

import io.github.sgrishchenko.karakum.generateTests
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class RelativeImportAutoTest {
    @Test
    fun test() = runTest {
        generateTests("relativeImportAuto") { testOutput ->
            input = listOf("**/*.d.ts")
            output = testOutput
            libraryName = "sandbox-relativeImportAuto"
            // No importMapper — auto-resolution from path
        }
    }
}

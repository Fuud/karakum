package io.github.sgrishchenko.karakum.relativeImport

import io.github.sgrishchenko.karakum.generateTests
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class RelativeImportAutoDirTest {
    @Test
    fun test() = runTest {
        generateTests("relativeImportAutoDir") { testOutput ->
            input = listOf("**/*.d.ts")
            output = testOutput
            libraryName = "sandbox-relativeImportAutoDir"
            isolatedOutputPackage = false
            // No importMapper — auto-resolution from path
        }
    }
}

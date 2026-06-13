package io.github.sgrishchenko.karakum.relativeImport

import io.github.sgrishchenko.karakum.generateTests
import io.github.sgrishchenko.karakum.util.ruleOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class RelativeImportTest {
    @Test
    fun test() = runTest {
        generateTests("relativeImport") { testOutput ->
            input = listOf("**/*.d.ts")
            output = testOutput
            libraryName = "sandbox-relativeImport"
            importMapper = mapOf(
                "^\\./core/Logger" to ruleOf("sandbox.relativeImport.core.Logger"),
                "^\\./app/Service" to ruleOf("sandbox.relativeImport.app.Service"),
            )
        }
    }
}

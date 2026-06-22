package io.github.sgrishchenko.karakum.importDefault

import io.github.sgrishchenko.karakum.generateTests
import io.github.sgrishchenko.karakum.util.ruleOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ImportDefaultTest {
    @Test
    fun test() = runTest {
        generateTests("importDefault") { testOutput ->
            input = listOf("**/*.d.ts")
            output = testOutput
            libraryName = "sandbox-importDefault"
            importMapper = mapOf(
                "^default-provider$" to ruleOf("default.provider"),
                "other-default-provider" to ruleOf(
                    "default" to "other.default.provider.",
                    ".+" to "other.default.provider.",
                ),
            )
        }
    }
}

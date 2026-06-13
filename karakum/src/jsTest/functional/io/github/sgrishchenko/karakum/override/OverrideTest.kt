package io.github.sgrishchenko.karakum.override

import io.github.sgrishchenko.karakum.generateTests
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class OverrideTest {
    @Test
    fun test() = runTest {
        generateTests("override") { testOutput ->
            input = listOf("**/*.d.ts")
            output = testOutput
            libraryName = "sandbox-override"
        }
    }
}

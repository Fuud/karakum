package io.github.sgrishchenko.karakum.casing

import io.github.sgrishchenko.karakum.generateTests
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class CasingTest {
    @Test
    fun test() = runTest {
        generateTests("casing") { testOutput ->
            input = listOf("**/*.d.ts")
            output = testOutput
            libraryName = "sandbox-casing"
        }
    }
}

package io.github.sgrishchenko.karakum.typemapper

import io.github.sgrishchenko.karakum.generateTests
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class TypeMapperTest {
    @Test
    fun test() = runTest {
        generateTests("typemapper") { testOutput ->
            input = listOf("**/*.d.ts")
            output = testOutput
            libraryName = "sandbox-typemapper"
            typeMapper = mapOf(
                "^CanvasImageSourceWebCodecs$" to "sandbox.typemapper.ambientTypes.",
                "^VideoFrameCallback$" to "sandbox.typemapper.ambientTypes.VideoFrameCallback"
            )
        }
    }
}

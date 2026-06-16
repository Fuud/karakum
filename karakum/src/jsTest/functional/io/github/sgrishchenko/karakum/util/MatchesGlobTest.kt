package io.github.sgrishchenko.karakum.util

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MatchesGlobTest {
    @Test
    fun doubleStarSlashFastPath() {
        val path = "C:/Users/test/.hidden/project/interface/inheritance.d.ts"
        assertTrue(matchesGlob(path, "**/interface/inheritance.d.ts"))
        assertFalse(matchesGlob(path, "**/class/parentConstructors.d.ts"))
    }

    @Test
    fun doubleStarSlashWithBackslashes() {
        val path = "C:\\Users\\test\\.hidden\\project\\interface\\inheritance.d.ts"
        assertTrue(matchesGlob(path, "**/interface/inheritance.d.ts"))
    }

    @Test
    fun doubleStarSlashWithWildcardsInSuffix() {
        val path = "src/interface/inheritance.d.ts"
        assertTrue(matchesGlob(path, "**/*.d.ts"))
        assertTrue(matchesGlob(path, "**/interface/*.d.ts"))
        assertFalse(matchesGlob(path, "**/class/*.d.ts"))
    }

    @Test
    fun doubleStarWithoutSlash() {
        val path = "src/test.d.ts"
        assertTrue(matchesGlob(path, "**"))
    }

    @Test
    fun singleStar() {
        assertTrue(matchesGlob("src/foo.d.ts", "src/*.d.ts"))
        assertFalse(matchesGlob("src/sub/foo.d.ts", "src/*.d.ts"))
    }

    @Test
    fun questionMark() {
        assertTrue(matchesGlob("src/foo.d.ts", "src/fo?.d.ts"))
        assertFalse(matchesGlob("src/foo.d.ts", "src/f?.d.ts"))
    }

    @Test
    fun specialChars() {
        assertTrue(matchesGlob("src/foo(bar).d.ts", "src/foo(bar).d.ts"))
        assertTrue(matchesGlob("src/foo[0].d.ts", "src/foo[0].d.ts"))
    }

    @Test
    fun dotPrefixedDirectory() {
        val path = "/home/user/.config/project/file.d.ts"
        assertTrue(matchesGlob(path, "**/file.d.ts"))
        assertTrue(matchesGlob(path, "**/project/file.d.ts"))
        assertFalse(matchesGlob(path, "**/other.d.ts"))
    }
}

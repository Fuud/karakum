package io.github.sgrishchenko.karakum.util

import js.regexp.RegExp

private val regexCache = mutableMapOf<String, RegExp>()

private fun globToRegex(pattern: String): RegExp {
    return regexCache.getOrPut(pattern) {
        val normalizedPattern = pattern.replace('\\', '/')

        var regex = ""
        var i = 0
        while (i < normalizedPattern.length) {
            when {
                normalizedPattern[i] == '*' && i + 1 < normalizedPattern.length && normalizedPattern[i + 1] == '*' -> {
                    if (i + 2 < normalizedPattern.length && normalizedPattern[i + 2] == '/') {
                        regex += "(?:[^/]*/)*"
                        i += 3
                    } else {
                        regex += ".*"
                        i += 2
                    }
                }
                normalizedPattern[i] == '*' -> {
                    regex += "[^/]*"
                    i++
                }
                normalizedPattern[i] == '?' -> {
                    regex += "[^/]"
                    i++
                }
                ".+^\${}()|[]".contains(normalizedPattern[i]) -> {
                    regex += "\\${normalizedPattern[i]}"
                    i++
                }
                else -> {
                    regex += normalizedPattern[i]
                    i++
                }
            }
        }

        RegExp("^$regex$")
    }
}

fun matchesGlob(filePath: String, pattern: String): Boolean {
    val normalized = filePath.replace('\\', '/')

    // Fast path: **/suffix — just check if path ends with /suffix
    if (pattern.startsWith("**/") && !pattern.substring(3).contains('*') && !pattern.substring(3).contains('?')) {
        val suffix = "/" + pattern.substring(3)
        return normalized.endsWith(suffix)
    }

    return globToRegex(pattern).test(normalized)
}

package io.github.sgrishchenko.karakum.util

import js.array.ReadonlyArray
import js.objects.ReadonlyRecord
import js.objects.recordOf
import kotlinx.js.JsPlainObject

@JsPlainObject
external interface NormalizationResult<T> {
    val items: ReadonlyArray<T>
    val conflicts: ReadonlyRecord<String, ReadonlyArray<T>>
}

fun <T> normalizeItems(
    items: ReadonlyArray<T>,
    keySelector: (item: T) -> String,
    keyNormalizer: (String) -> String = { it },
    merge: (key: String, item: T, other: T) -> T?,
): NormalizationResult<T> {
    val result = mutableMapOf<String, T>()
    val conflicts = mutableMapOf<String, MutableList<T>>()

    for (item in items) {
        val key = keySelector(item)
        val normalizedKey = keyNormalizer(key)
        val existingItem = result[normalizedKey]

        if (existingItem == null) {
            result[normalizedKey] = item
        } else {
            val mergedItem = merge(key, existingItem, item)

            if (mergedItem == null) {
                var existingConflict = conflicts[normalizedKey]

                if (existingConflict == null) {
                    existingConflict = mutableListOf(existingItem)
                    conflicts[normalizedKey] = existingConflict
                }

                existingConflict += item
            } else {
                result[normalizedKey] = mergedItem
            }
        }
    }

    return NormalizationResult(
        items = result.values.toTypedArray(),
        conflicts = recordOf(
            pairs = conflicts
                .map { it.key to it.value.toTypedArray() }
                .toTypedArray()
        )
    )
}

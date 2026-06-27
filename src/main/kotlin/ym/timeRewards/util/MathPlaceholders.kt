package ym.timeRewards.util

import kotlin.random.Random

private val mathPlaceholderPattern = Regex("%math_(-?\\d+)-(-?\\d+)%")

fun String.applyMathPlaceholders(random: Random = Random.Default): String {
    return mathPlaceholderPattern.replace(this) { match ->
        val min = match.groupValues[1].toIntOrNull()
        val max = match.groupValues[2].toIntOrNull()
        if (min == null || max == null) {
            match.value
        } else {
            val low = min.coerceAtMost(max)
            val high = min.coerceAtLeast(max)
            val value = if (low == high) {
                low.toLong()
            } else {
                random.nextLong(low.toLong(), high.toLong() + 1L)
            }
            value.toString()
        }
    }
}

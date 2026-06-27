package ym.timeRewards.util

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MathPlaceholdersTest {
    @Test
    fun replacesInclusiveRange() {
        val random = Random(1234)
        val value = "%math_1-200%".applyMathPlaceholders(random).toInt()
        assertTrue(value in 1..200)
    }

    @Test
    fun supportsSingleValueRange() {
        val random = Random(1234)
        assertEquals("7", "%math_7-7%".applyMathPlaceholders(random))
    }

    @Test
    fun normalizesReversedRange() {
        val random = Random(1234)
        val value = "%math_200-1%".applyMathPlaceholders(random).toInt()
        assertTrue(value in 1..200)
    }
}

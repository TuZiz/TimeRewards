package ym.timeRewards.model

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.WeekFields
import java.util.Locale

enum class RewardScope(
    val key: String,
    val displayName: String,
) {
    TODAY("today", "今日在线奖励"),
    WEEK("week", "本周在线奖励"),
    MONTH("month", "本月在线奖励"),
    YEAR("year", "本年在线奖励"),
    TOTAL("total", "累计在线奖励"),
    ;

    fun currentToken(now: LocalDate = LocalDate.now()): String {
        return when (this) {
            TODAY -> now.toString()
            WEEK -> {
                val weekFields = WeekFields.of(Locale.getDefault())
                val week = now.get(weekFields.weekOfWeekBasedYear())
                val year = now.get(weekFields.weekBasedYear())
                "$year-W${week.toString().padStart(2, '0')}"
            }
            MONTH -> YearMonth.from(now).toString()
            YEAR -> now.year.toString()
            TOTAL -> "all-time"
        }
    }

    companion object {
        fun fromKey(key: String?): RewardScope? = entries.firstOrNull { it.key.equals(key, ignoreCase = true) }
    }
}

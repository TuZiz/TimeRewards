package ym.timeRewards.placeholder

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer
import ym.timeRewards.TimeRewards
import ym.timeRewards.model.RewardScope
import ym.timeRewards.util.formatDuration

class TimeRewardsPlaceholderExpansion(
    private val plugin: TimeRewards,
) : PlaceholderExpansion() {

    override fun getIdentifier(): String = "timerewards"

    override fun getAuthor(): String = plugin.description.authors.joinToString()

    override fun getVersion(): String = plugin.description.version

    override fun persist(): Boolean = true

    override fun onRequest(player: OfflinePlayer?, params: String): String {
        if (player == null) {
            return ""
        }
        val profile = plugin.playerDataService.getOrLoad(player.uniqueId, player.name ?: player.uniqueId.toString())
        val current = RewardScope.entries.associateWith { scope ->
            val onlinePlayer = player.player
            if (onlinePlayer != null) plugin.trackingService.currentSeconds(onlinePlayer, scope)
            else plugin.trackingService.currentSeconds(profile, scope)
        }

        return when (params.lowercase()) {
            "today_minutes" -> ((current[RewardScope.TODAY] ?: 0L) / 60L).toString()
            "week_minutes" -> ((current[RewardScope.WEEK] ?: 0L) / 60L).toString()
            "month_minutes" -> ((current[RewardScope.MONTH] ?: 0L) / 60L).toString()
            "year_minutes" -> ((current[RewardScope.YEAR] ?: 0L) / 60L).toString()
            "total_minutes" -> ((current[RewardScope.TOTAL] ?: 0L) / 60L).toString()
            "today_formatted" -> formatDuration(current[RewardScope.TODAY] ?: 0L)
            "week_formatted" -> formatDuration(current[RewardScope.WEEK] ?: 0L)
            "month_formatted" -> formatDuration(current[RewardScope.MONTH] ?: 0L)
            "year_formatted" -> formatDuration(current[RewardScope.YEAR] ?: 0L)
            "total_formatted" -> formatDuration(current[RewardScope.TOTAL] ?: 0L)
            else -> ""
        }
    }
}

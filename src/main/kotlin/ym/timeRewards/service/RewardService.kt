package ym.timeRewards.service

import me.clip.placeholderapi.PlaceholderAPI
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import ym.timeRewards.TimeRewards
import ym.timeRewards.config.ConfigManager
import ym.timeRewards.data.PlayerDataService
import ym.timeRewards.model.RewardDefinition
import ym.timeRewards.model.RewardScope
import ym.timeRewards.util.applyNameAndLore
import ym.timeRewards.util.applyMathPlaceholders
import java.util.logging.Level

class RewardService(
    private val plugin: TimeRewards,
    private val configManager: ConfigManager,
    private val playerDataService: PlayerDataService,
    private val trackingService: TimeTrackingService,
) {
    data class ClaimResult(
        val messageKey: String,
        val placeholders: Map<String, String> = emptyMap(),
        val refreshGui: Boolean = false,
    )

    fun claim(player: Player, scope: RewardScope, reward: RewardDefinition): ClaimResult {
        playerDataService.getOrLoad(player.uniqueId, player.name)
        val seconds = trackingService.currentSeconds(player, scope)
        val progress = playerDataService.get(player.uniqueId)?.scopeData?.get(scope)
            ?: return ClaimResult("reward-not-ready")

        if (reward.id in progress.claimedRewardIds) {
            return ClaimResult("reward-already-claimed")
        }

        if (seconds < reward.requiredMinutes * 60L) {
            return ClaimResult(
                "reward-not-ready",
                mapOf(
                    "current" to (seconds / 60L).toString(),
                    "required" to reward.requiredMinutes.toString(),
                ),
                refreshGui = true,
            )
        }

        progress.claimedRewardIds += reward.id
        if (!playerDataService.save(player.uniqueId)) {
            progress.claimedRewardIds -= reward.id
            return ClaimResult(
                messageKey = "reward-save-failed",
                placeholders = mapOf("reward" to reward.name),
                refreshGui = true,
            )
        }

        val failedCommands = mutableListOf<String>()
        reward.commands.forEach { command ->
            val preparedCommand = replacePlaceholders(player, reward, command)
            val executed = try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), preparedCommand)
            } catch (exception: Exception) {
                plugin.logger.log(Level.WARNING, "Failed to dispatch reward command for ${player.name}: $preparedCommand", exception)
                false
            }
            if (!executed) {
                failedCommands += preparedCommand
                plugin.logger.warning("Reward command returned false for ${player.name}: $preparedCommand")
            }
        }
        reward.items.forEach { itemReward ->
            val item = ItemStack(itemReward.material, itemReward.amount).applyNameAndLore(itemReward.name, itemReward.lore)
            val remaining = player.inventory.addItem(item)
            remaining.values.forEach { leftover -> player.world.dropItemNaturally(player.location, leftover) }
        }

        return ClaimResult(
            messageKey = if (failedCommands.isEmpty()) "reward-claimed" else "reward-partial-claimed",
            placeholders = mapOf(
                "reward" to reward.name,
                "failed" to failedCommands.size.toString(),
            ),
            refreshGui = true,
        )
    }

    fun hasClaimed(playerId: java.util.UUID, scope: RewardScope, rewardId: String): Boolean {
        return playerDataService.get(playerId)?.scopeData?.get(scope)?.claimedRewardIds?.contains(rewardId) == true
    }

    private fun replacePlaceholders(player: Player, reward: RewardDefinition, input: String): String {
        var output = input
            .replace("{player}", player.name)
            .replace("{player_name}", player.name)
            .replace("{reward}", reward.name)
            .replace("{time_required}", reward.requiredMinutes.toString())
            .replace("{scope}", reward.scope.key)
            .applyMathPlaceholders()

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            output = PlaceholderAPI.setPlaceholders(player, output)
        }
        return output.applyMathPlaceholders()
    }
}

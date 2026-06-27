package ym.timeRewards.command

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import ym.timeRewards.TimeRewards
import ym.timeRewards.model.RewardDefinition
import ym.timeRewards.model.RewardScope
import ym.timeRewards.util.colorize

class TimeRewardsCommand(
    private val plugin: TimeRewards,
) : CommandExecutor, TabCompleter {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>,
    ): Boolean {
        if (args.isNotEmpty() && args[0].equals("reload", ignoreCase = true)) {
            if (!sender.hasPermission("timerewards.reload")) {
                sender.sendMessage(plugin.configManager.message("no-permission"))
                return true
            }
            plugin.reloadPlugin()
            sender.sendMessage(plugin.configManager.message("reload-success"))
            return true
        }

        val player = sender as? Player ?: run {
            sender.sendMessage(plugin.configManager.message("player-only"))
            return true
        }
        if (!player.hasPermission("timerewards.use")) {
            player.sendMessage(plugin.configManager.message("no-permission"))
            return true
        }

        if (args.isEmpty() || args[0].equals("open", ignoreCase = true) || args[0].equals("today", ignoreCase = true)) {
            plugin.rewardGuiService.open(player, RewardScope.TODAY)
            return true
        }

        if (args[0].equals("claim", ignoreCase = true)) {
            return handleClaim(player, args)
        }

        val scope = RewardScope.fromKey(args[0])
        if (scope == null) {
            player.sendMessage(plugin.configManager.message("command-usage"))
            return true
        }

        plugin.rewardGuiService.open(player, scope)
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>,
    ): MutableList<String> {
        if (args.size == 1) {
            return listOf("open", "today", "week", "month", "year", "total", "claim", "reload")
                .filter { it.startsWith(args[0], ignoreCase = true) }
                .toMutableList()
        }
        if (args.size == 2 && args[0].equals("claim", ignoreCase = true)) {
            return RewardScope.entries.map { it.key }
                .filter { it.startsWith(args[1], ignoreCase = true) }
                .toMutableList()
        }
        if (args.size == 3 && args[0].equals("claim", ignoreCase = true)) {
            val scope = RewardScope.fromKey(args[1]) ?: return mutableListOf()
            return plugin.configManager.rewards(scope)
                .mapIndexed { index, reward -> listOf(reward.id, (index + 1).toString()) }
                .flatten()
                .distinct()
                .filter { it.startsWith(args[2], ignoreCase = true) }
                .toMutableList()
        }
        return mutableListOf()
    }

    private fun handleClaim(player: Player, args: Array<out String>): Boolean {
        if (args.size < 3) {
            player.sendMessage(plugin.configManager.message("command-claim-usage"))
            return true
        }
        val scope = RewardScope.fromKey(args[1])
        if (scope == null) {
            player.sendMessage(plugin.configManager.message("command-unknown-scope", mapOf("scope" to args[1])))
            return true
        }
        val reward = resolveReward(scope, args[2])
        if (reward == null) {
            player.sendMessage(plugin.configManager.message("command-reward-not-found", mapOf("target" to args[2])))
            return true
        }
        val result = plugin.rewardService.claim(player, scope, reward)
        player.sendMessage(plugin.configManager.message(result.messageKey, result.placeholders))
        return true
    }

    private fun sendScopeList(player: Player, scope: RewardScope) {
        plugin.configManager.messageList(
            "command-scope-header",
            mapOf(
                "scope" to plugin.configManager.scopeDisplay(scope),
                "scope_key" to scope.key,
            ),
        ).forEach(player::sendMessage)

        val currentMinutes = plugin.trackingService.currentMinutes(player, scope)
        plugin.configManager.rewards(scope).forEachIndexed { index, reward ->
            val status = when {
                plugin.rewardService.hasClaimed(player.uniqueId, scope, reward.id) -> "已领取"
                currentMinutes >= reward.requiredMinutes -> "可领取"
                else -> "未达成"
            }
            val statusColor = when (status) {
                "已领取" -> "&a"
                "可领取" -> "&e"
                else -> "&c"
            }
            val claimTip = if (status == "可领取") {
                "输入 /tr claim ${scope.key} ${index + 1} 领取"
            } else {
                "达到条件后可输入 /tr claim ${scope.key} ${index + 1}"
            }

            val placeholders = mapOf(
                "index" to (index + 1).toString(),
                "id" to reward.id,
                "reward" to (reward.displayName ?: reward.name),
                "progress" to currentMinutes.toString(),
                "required" to reward.requiredMinutes.toString(),
                "status" to status,
                "status_color" to statusColor,
                "claim_tip" to claimTip,
                "command_count" to reward.commands.size.toString(),
                "item_count" to reward.items.size.toString(),
            )

            plugin.configManager.messageList("command-scope-entry", placeholders).forEach { line ->
                if (line == "{display_reward_lines}") {
                    val rewardLines = reward.displayRewards.ifEmpty {
                        plugin.configManager.messageList("command-scope-entry-fallback", placeholders)
                    }
                    rewardLines.forEach { rewardLine ->
                        val output = if (rewardLine.startsWith("&") || rewardLine.startsWith("#")) rewardLine else "&8 • &f$rewardLine"
                        player.sendMessage(output.colorize())
                    }
                } else {
                    player.sendMessage(line)
                }
            }
        }

        plugin.configManager.messageList(
            "command-scope-footer",
            mapOf("scope_key" to scope.key),
        ).forEach(player::sendMessage)
    }

    private fun resolveReward(scope: RewardScope, target: String): RewardDefinition? {
        val rewards = plugin.configManager.rewards(scope)
        val index = target.toIntOrNull()
        if (index != null) {
            return rewards.getOrNull(index - 1)
        }
        return rewards.firstOrNull { it.id.equals(target, ignoreCase = true) }
    }
}

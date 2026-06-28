package ym.timeRewards.service

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import ym.easygui.api.ChestScreen
import ym.easygui.api.ClickContext
import ym.easygui.api.SlotBuilder
import ym.easygui.api.chestGui
import ym.easygui.core.GuiManager
import ym.timeRewards.config.ConfigManager
import ym.timeRewards.config.GuiKeyTemplate
import ym.timeRewards.data.PlayerDataService
import ym.timeRewards.model.ItemReward
import ym.timeRewards.model.RewardDefinition
import ym.timeRewards.model.RewardScope
import ym.timeRewards.util.buildDisplayItem
import ym.timeRewards.util.formatDuration

class RewardGuiService(
    private val configManager: ConfigManager,
    private val playerDataService: PlayerDataService,
    private val trackingService: TimeTrackingService,
    private val rewardService: RewardService,
    private val guiManager: GuiManager,
) {
    private val listSeparator = "\u001F"

    fun open(player: Player, scope: RewardScope) {
        playerDataService.getOrLoad(player.uniqueId, player.name)
        guiManager.open(player, createScreen(scope, 0))
    }

    private fun createScreen(scope: RewardScope, page: Int): ChestScreen {
        val gui = configManager.guiConfig(scope)
        val rewardSlots = layoutSlots(gui, "reward")
        val rewards = configManager.rewards(scope)
        val explicitRewardReferences = configManager.guiRewardReferences(scope)
        val explicitRewardKeys = explicitRewardReferences.toSet()
        val genericRewards = if (explicitRewardKeys.isEmpty()) rewards else emptyList()
        val visibleRewardCount = if (explicitRewardKeys.isEmpty()) rewards.size else explicitRewardKeys.size
        val pageSize = if (explicitRewardKeys.isEmpty()) rewardSlots.size else 0
        val pageCount = when {
            genericRewards.isEmpty() || pageSize == 0 -> 1
            else -> ((genericRewards.size - 1) / pageSize) + 1
        }
        val safePage = page.coerceIn(0, pageCount - 1)
        val pageRewards = if (pageSize == 0) emptyList() else genericRewards.drop(safePage * pageSize).take(pageSize)

        return chestGui(gui.rows, configManager.guiTitle(scope)) {
            gui.layout.forEachIndexed { row, cells ->
                cells.forEachIndexed cellLoop@{ column, cell ->
                    val slotIndex = row * 9 + column
                    val rewardReference = cell.rewardReference
                    if (rewardReference != null) {
                        val reward = configManager.resolveGuiReward(scope, rewardReference) ?: return@cellLoop
                        slot(slotIndex) {
                            readOnly()
                            bindRewardSlot(this, scope, configManager.rewardTemplate(scope), reward)
                        }
                        return@cellLoop
                    }
                    val key = cell.key ?: return@cellLoop
                    val template = gui.keys[key] ?: return@cellLoop
                    slot(slotIndex) {
                        readOnly()
                        when (template.iconFunction?.lowercase()) {
                            null -> item(renderTemplate(template))
                            "reward" -> bindPagedRewardSlot(this, scope, template, slotIndex, rewardSlots, pageRewards)
                            "stats" -> item { player, _ -> buildStatsItem(player, template) }
                            "auto_claim" -> {
                                item { player, _ -> buildAutoClaimItem(player, template) }
                                onClick { ctx: ClickContext ->
                                    val enabled = !playerDataService.isAutoClaimEnabled(ctx.player.uniqueId)
                                    if (playerDataService.setAutoClaimEnabled(ctx.player.uniqueId, ctx.player.name, enabled)) {
                                        val messageKey = if (enabled) "auto-claim-enabled" else "auto-claim-disabled"
                                        ctx.player.sendMessage(configManager.message(messageKey))
                                        if (enabled) {
                                            rewardService.autoClaimAvailable(ctx.player)
                                        }
                                    } else {
                                        ctx.player.sendMessage(configManager.message("auto-claim-save-failed"))
                                    }
                                    ctx.refresh()
                                }
                            }
                            "page" -> item(
                                renderTemplate(
                                    template,
                                    mapOf(
                                        "page" to (safePage + 1).toString(),
                                        "pages" to pageCount.toString(),
                                        "reward_count" to visibleRewardCount.toString(),
                                    ),
                                ),
                            )
                            "previous" -> {
                                item(renderTemplate(template))
                                onClick { ctx: ClickContext ->
                                    if (safePage > 0) ctx.replace(createScreen(scope, safePage - 1))
                                }
                            }
                            "next" -> {
                                item(renderTemplate(template))
                                onClick { ctx: ClickContext ->
                                    if (safePage + 1 < pageCount) ctx.replace(createScreen(scope, safePage + 1))
                                }
                            }
                            "close" -> {
                                item(renderTemplate(template))
                                onClick { ctx: ClickContext -> ctx.close() }
                            }
                            else -> item(renderTemplate(template))
                        }
                    }
                }
            }
        }
    }

    private fun bindPagedRewardSlot(
        slotBuilder: SlotBuilder,
        scope: RewardScope,
        template: GuiKeyTemplate,
        slotIndex: Int,
        rewardSlots: List<Int>,
        pageRewards: List<RewardDefinition>,
    ) {
        val rewardIndex = rewardSlots.indexOf(slotIndex)
        val reward = pageRewards.getOrNull(rewardIndex)
        if (reward == null) {
            slotBuilder.item(renderTemplate(template))
            return
        }

        bindRewardSlot(slotBuilder, scope, template, reward)
    }

    private fun bindRewardSlot(
        slotBuilder: SlotBuilder,
        scope: RewardScope,
        template: GuiKeyTemplate,
        reward: RewardDefinition,
    ) {
        slotBuilder.item { player, _ -> buildRewardItem(player, template, reward) }
        slotBuilder.onClick { ctx: ClickContext ->
            val result = rewardService.claim(ctx.player, scope, reward)
            ctx.player.sendMessage(configManager.message(result.messageKey, result.placeholders))
            if (result.refreshGui) ctx.refresh()
        }
    }

    private fun buildStatsItem(player: Player, template: GuiKeyTemplate): ItemStack {
        return renderTemplate(
            template,
            mapOf(
                "today_time" to formatDuration(trackingService.currentSeconds(player, RewardScope.TODAY)),
                "week_time" to formatDuration(trackingService.currentSeconds(player, RewardScope.WEEK)),
                "month_time" to formatDuration(trackingService.currentSeconds(player, RewardScope.MONTH)),
                "year_time" to formatDuration(trackingService.currentSeconds(player, RewardScope.YEAR)),
                "total_time" to formatDuration(trackingService.currentSeconds(player, RewardScope.TOTAL)),
            ),
            player,
        )
    }

    private fun buildAutoClaimItem(player: Player, template: GuiKeyTemplate): ItemStack {
        val enabled = playerDataService.isAutoClaimEnabled(player.uniqueId)
        return renderTemplate(
            template.copy(
                material = if (enabled) Material.LIME_DYE.name else Material.GRAY_DYE.name,
                glow = enabled,
            ),
            mapOf(
                "auto_claim_status" to if (enabled) "已开启" else "已关闭",
                "auto_claim_status_color" to if (enabled) "&#10AC84" else "&#A0A0A0",
                "auto_claim_toggle" to if (enabled) "点击关闭自动领取" else "点击开启自动领取",
            ),
            player,
        )
    }

    private fun buildRewardItem(player: Player, template: GuiKeyTemplate, reward: RewardDefinition): ItemStack {
        val currentSeconds = trackingService.currentSeconds(player, reward.scope)
        val currentMinutes = currentSeconds / 60L
        val claimed = rewardService.hasClaimed(player.uniqueId, reward.scope, reward.id)
        val achieved = currentSeconds >= reward.requiredMinutes * 60L

        val status = when {
            claimed -> "已领取"
            achieved -> "可领取"
            else -> "未达成"
        }
        val hint = when {
            claimed -> "该奖励不可重复领取"
            achieved -> "点击领取"
            else -> "当前不可领取"
        }

        val placeholders = mapOf(
            "reward" to reward.name,
            "reward_display" to (reward.displayName ?: reward.name),
            "required_minutes" to reward.requiredMinutes.toString(),
            "current_minutes" to currentMinutes.toString(),
            "current_time" to formatDuration(currentSeconds),
            "status" to status,
            "hint" to hint,
            "command_count" to reward.commands.size.toString(),
            "item_count" to reward.items.size.toString(),
            "display_reward_count" to reward.displayRewards.size.toString(),
            "progress_text" to "$currentMinutes / ${reward.requiredMinutes} 分钟",
            "commands_text" to reward.commands.joinToString(listSeparator),
            "items_text" to reward.items.joinToString(listSeparator) { formatItemReward(it) },
            "display_rewards_text" to reward.displayRewards.joinToString(listSeparator),
            "rewards_text" to buildRewardSummaryText(reward),
        )

        val actualTemplate = template.copy(
            material = (reward.icon ?: Material.matchMaterial(template.material) ?: Material.CHEST).name,
            amount = reward.iconAmount,
            name = reward.displayName ?: template.name,
            lore = if (reward.lore.isNotEmpty()) reward.lore else template.lore,
            glow = if (claimed) false else (template.glow || achieved),
        )

        return renderTemplate(actualTemplate, placeholders, player)
    }

    private fun renderTemplate(
        template: GuiKeyTemplate,
        placeholders: Map<String, String> = emptyMap(),
        viewer: Player? = null,
    ): ItemStack {
        val material = Material.matchMaterial(template.material) ?: Material.STONE
        return buildDisplayItem(
            material = material,
            amount = template.amount,
            name = replace(template.name, placeholders),
            lore = expandLore(template.lore, placeholders),
            glow = template.glow,
            headOwner = if (material == Material.PLAYER_HEAD) viewer else null,
        )
    }

    private fun expandLore(lore: List<String>, placeholders: Map<String, String>): List<String> {
        val expanded = mutableListOf<String>()
        lore.forEach { line ->
            when (line.trim()) {
                "{display_reward_lines}" -> placeholders["display_rewards_text"]
                    ?.takeIf { it.isNotBlank() }
                    ?.split(listSeparator)
                    ?.forEach { expanded += "&8 • &f$it" }

                "{commands_lines}" -> placeholders["commands_text"]
                    ?.takeIf { it.isNotBlank() }
                    ?.split(listSeparator)
                    ?.forEach { expanded += "&8 • &7$it" }

                "{items_lines}" -> placeholders["items_text"]
                    ?.takeIf { it.isNotBlank() }
                    ?.split(listSeparator)
                    ?.forEach { expanded += "&8 • &7$it" }

                "{reward_lines}" -> expanded += buildRewardLines(placeholders)
                else -> expanded += replace(line, placeholders)
            }
        }
        return expanded
    }

    private fun buildRewardLines(placeholders: Map<String, String>): List<String> {
        val lines = mutableListOf<String>()
        placeholders["display_rewards_text"]?.takeIf { it.isNotBlank() }?.split(listSeparator)?.forEach {
            lines += "&8 • &f$it"
        }
        if (lines.isEmpty()) {
            placeholders["items_text"]?.takeIf { it.isNotBlank() }?.split(listSeparator)?.forEach {
                lines += "&8 • &f$it"
            }
        }
        if (lines.isEmpty()) {
            val commandCount = placeholders["command_count"] ?: "0"
            val itemCount = placeholders["item_count"] ?: "0"
            lines += "&8 • &7包含 $commandCount 条指令奖励 / $itemCount 项物品奖励"
        }
        return lines
    }

    private fun buildRewardSummaryText(reward: RewardDefinition): String {
        val entries = mutableListOf<String>()
        entries += reward.displayRewards
        if (entries.isEmpty()) entries += reward.items.map(::formatItemReward)
        return entries.joinToString(listSeparator)
    }

    private fun formatItemReward(itemReward: ItemReward): String {
        val display = itemReward.name ?: itemReward.material.name.lowercase().replace('_', ' ')
        return "$display x${itemReward.amount}"
    }

    private fun layoutSlots(gui: ym.timeRewards.config.GuiConfig, functionName: String): List<Int> {
        val slots = mutableListOf<Int>()
        gui.layout.forEachIndexed { row, cells ->
            cells.forEachIndexed cellLoop@{ column, cell ->
                val key = cell.key ?: return@cellLoop
                if (gui.keys[key]?.iconFunction?.equals(functionName, ignoreCase = true) == true) {
                    slots += row * 9 + column
                }
            }
        }
        return slots
    }

    private fun replace(input: String, placeholders: Map<String, String>): String {
        var output = input
        placeholders.forEach { (key, value) -> output = output.replace("{$key}", value) }
        return output
    }
}

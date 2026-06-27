package ym.timeRewards.model

import org.bukkit.Material

data class RewardDefinition(
    val id: String,
    val scope: RewardScope,
    val name: String,
    val displayName: String? = null,
    val lore: List<String> = emptyList(),
    val displayRewards: List<String> = emptyList(),
    val iconAmount: Int = 1,
    val requiredMinutes: Long,
    val commands: List<String>,
    val items: List<ItemReward>,
    val icon: Material? = null,
)

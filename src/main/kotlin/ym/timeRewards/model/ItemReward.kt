package ym.timeRewards.model

import org.bukkit.Material

data class ItemReward(
    val material: Material,
    val amount: Int,
    val name: String? = null,
    val lore: List<String> = emptyList(),
)

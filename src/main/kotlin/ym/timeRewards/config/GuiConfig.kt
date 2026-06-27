package ym.timeRewards.config

data class GuiConfig(
    val title: String,
    val rows: Int,
    val layout: List<List<GuiLayoutCell>>,
    val keys: Map<Char, GuiKeyTemplate>,
    val sourceName: String,
)

data class GuiKeyTemplate(
    val material: String,
    val amount: Int = 1,
    val name: String = " ",
    val lore: List<String> = emptyList(),
    val glow: Boolean = false,
    val iconFunction: String? = null,
)

data class GuiLayoutCell(
    val key: Char? = null,
    val rewardReference: String? = null,
)

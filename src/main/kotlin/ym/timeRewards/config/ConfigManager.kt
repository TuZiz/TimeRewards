package ym.timeRewards.config

import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import ym.timeRewards.TimeRewards
import ym.timeRewards.model.ItemReward
import ym.timeRewards.model.RewardDefinition
import ym.timeRewards.model.RewardScope
import ym.timeRewards.util.colorize
import java.io.File

class ConfigManager(
    private val plugin: TimeRewards,
) {
    private val rewardFiles = mapOf(
        RewardScope.TODAY to "day.yml",
        RewardScope.WEEK to "week.yml",
        RewardScope.MONTH to "month.yml",
        RewardScope.YEAR to "year.yml",
        RewardScope.TOTAL to "total.yml",
    )

    private val guiFiles = mapOf(
        RewardScope.TODAY to "day.yml",
        RewardScope.WEEK to "week.yml",
        RewardScope.MONTH to "month.yml",
        RewardScope.YEAR to "year.yml",
        RewardScope.TOTAL to "total.yml",
    )

    private lateinit var messages: YamlConfiguration
    private var guiConfigsByScope: Map<RewardScope, GuiConfig> = emptyMap()
    private var rewardScopeEnabledByScope: Map<RewardScope, Boolean> = emptyMap()

    val guiConfig: GuiConfig
        get() = guiConfig(defaultScope)

    var rewardsByScope: Map<RewardScope, List<RewardDefinition>> = emptyMap()
        private set

    val defaultScope: RewardScope
        get() = RewardScope.fromKey(plugin.config.getString("settings.default-scope")) ?: RewardScope.TODAY

    fun reload() {
        plugin.reloadConfig()
        messages = YamlConfiguration.loadConfiguration(File(plugin.dataFolder, "messages.yml"))
        guiConfigsByScope = RewardScope.entries.associateWith(::readGuiConfig)
        val enabledScopes = mutableMapOf<RewardScope, Boolean>()
        rewardsByScope = RewardScope.entries.associateWith { scope -> readRewards(scope, enabledScopes) }
        rewardScopeEnabledByScope = enabledScopes
        validateGuiRewardReferences()
    }

    fun rewards(scope: RewardScope): List<RewardDefinition> = rewardsByScope[scope].orEmpty()

    fun guiConfig(scope: RewardScope): GuiConfig = guiConfigsByScope[scope]
        ?: guiConfigsByScope[RewardScope.TODAY]
        ?: error("GUI 配置尚未加载")

    fun guiTitle(scope: RewardScope): String {
        return applyPlaceholders(
            guiConfig(scope).title,
            mapOf("scope" to scopeDisplay(scope), "scope_id" to scope.key),
        ).colorize()
    }

    fun scopeDisplay(scope: RewardScope): String {
        return messages.getString("scopes.${scope.key}", scope.displayName).orEmpty().colorize()
    }

    fun message(path: String, placeholders: Map<String, String> = emptyMap()): String {
        val raw = messages.getString("messages.$path", "&cMissing message: $path").orEmpty()
        return applyPlaceholders(raw, placeholders).colorize()
    }

    fun messageList(path: String, placeholders: Map<String, String> = emptyMap()): List<String> {
        val values = messages.getStringList("messages.$path")
        if (values.isEmpty()) {
            return listOf("&cMissing message list: $path".colorize())
        }
        return values.map { applyPlaceholders(it, placeholders).colorize() }
    }

    fun guiRewardReferences(scope: RewardScope): List<String> {
        return guiConfig(scope).layout.flatten().mapNotNull { it.rewardReference }
    }

    fun resolveGuiReward(scope: RewardScope, reference: String): RewardDefinition? {
        return rewards(scope).firstOrNull { reward ->
            reward.name.equals(reference, ignoreCase = true) ||
                reward.id.equals(reference, ignoreCase = true) ||
                (reward.displayName?.equals(reference, ignoreCase = true) == true)
        }
    }

    fun hasGenericRewardSlots(scope: RewardScope): Boolean {
        val gui = guiConfig(scope)
        return gui.layout.flatten().any { cell ->
            val key = cell.key ?: return@any false
            gui.keys[key]?.iconFunction?.equals("reward", ignoreCase = true) == true
        }
    }

    fun rewardTemplate(scope: RewardScope): GuiKeyTemplate {
        return guiConfig(scope).keys.values.firstOrNull {
            it.iconFunction?.equals("reward", ignoreCase = true) == true
        } ?: GuiKeyTemplate(
            material = "GOLD_BLOCK",
            name = "{reward_display}",
            lore = listOf(
                "",
                " &8▪ &7请在 Rewards 配置里自定义该奖励的 lore",
                " &e点击尝试领取",
                "",
            ),
            iconFunction = "reward",
        )
    }

    private fun readGuiConfig(scope: RewardScope): GuiConfig {
        val fileName = guiFiles.getValue(scope)
        val resourcePath = "gui/$fileName"
        val guiFile = File(plugin.dataFolder, resourcePath)
        if (!guiFile.exists()) {
            runCatching { plugin.saveResource(resourcePath, false) }
                .onFailure { exception ->
                    plugin.logger.severe("Missing GUI resource $resourcePath and failed to restore default: ${exception.message}")
                }
        }
        val section: ConfigurationSection? = YamlConfiguration.loadConfiguration(guiFile)
        val sourceName = resourcePath
        val layout = section?.getStringList("GuiPlain").orEmpty().ifEmpty {
            listOf(
                "#########",
                "#TWMYAS##",
                "#RRRRRRR#",
                "#RRRRRRR#",
                "#RRRRRRR#",
                "P###G###N",
            )
        }
        val keysSection = section?.getConfigurationSection("GuiKey")
        val keys = mutableMapOf<Char, GuiKeyTemplate>()

        keysSection?.getKeys(false)?.forEach { key ->
            if (key.length != 1) return@forEach
            val item = keysSection.getConfigurationSection(key) ?: return@forEach
            keys[key[0]] = GuiKeyTemplate(
                material = item.getString("Material", "STONE") ?: "STONE",
                amount = item.getInt("Amount", 1).coerceAtLeast(1),
                name = item.getString("Name", " ") ?: " ",
                lore = item.getStringList("Lore"),
                glow = item.getBoolean("Glint", false),
                iconFunction = item.getString("IconFunction"),
            )
        }

        return GuiConfig(
            title = section?.getString("Title", "&8[ &f✦ &8] &7&l在线奖励") ?: "&8[ &f✦ &8] &7&l在线奖励",
            rows = layout.size.coerceIn(1, 6),
            layout = layout.take(6).mapIndexed { rowIndex, pattern ->
                parseGuiRow(pattern, sourceName, rowIndex + 1)
            },
            keys = keys,
            sourceName = sourceName,
        )
    }

    private fun readRewards(scope: RewardScope, enabledScopes: MutableMap<RewardScope, Boolean>): List<RewardDefinition> {
        val fileName = rewardFiles.getValue(scope)
        val rewardsFile = File(plugin.dataFolder, "Rewards/$fileName")
        val section = if (rewardsFile.exists()) {
            val rewardsYaml = YamlConfiguration.loadConfiguration(rewardsFile)
            rewardsYaml.getConfigurationSection(scope.key) ?: rewardsYaml
        } else {
            plugin.logger.warning("Reward file Rewards/$fileName not found. Falling back to config.yml section '${scope.key}'.")
            plugin.config.getConfigurationSection(scope.key) ?: return emptyList()
        }
        val enabled = section.getBoolean("enabled", scope == RewardScope.TODAY)
        enabledScopes[scope] = enabled
        if (!enabled) {
            return emptyList()
        }

        val sourceName = if (rewardsFile.exists()) "Rewards/$fileName" else "config.yml:${scope.key}"
        return section.getKeys(false).mapNotNull { rewardName ->
            if (rewardName == "enabled") return@mapNotNull null
            val rewardSection = section.getConfigurationSection(rewardName) ?: return@mapNotNull null
            val requiredMinutes = rewardSection.getLong("time", -1L)
            if (requiredMinutes <= 0L) {
                plugin.logger.severe("$sourceName -> '$rewardName' 缺少有效 time，已跳过该奖励。")
                return@mapNotNull null
            }

            val commands = rewardSection.getStringList("commands")
            val items = readItems(rewardSection.getConfigurationSection("items"), "$sourceName -> '$rewardName'.items")
            if (commands.isEmpty() && items.isEmpty()) {
                plugin.logger.severe("$sourceName -> '$rewardName' 缺少实际奖励 commands 或 items，已跳过该奖励。")
                return@mapNotNull null
            }

            val iconName = rewardSection.getString("icon").orEmpty()
            val icon = iconName.takeIf { it.isNotBlank() }?.let {
                Material.matchMaterial(it).also { material ->
                    if (material == null) {
                        plugin.logger.warning("$sourceName -> '$rewardName' 的 icon '$iconName' 不是有效 Material，将使用 GUI 模板材质。")
                    }
                }
            }

            RewardDefinition(
                id = rewardName.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
                    .ifBlank { "reward_${scope.key}_${requiredMinutes}" },
                scope = scope,
                name = rewardName,
                displayName = rewardSection.getString("display-name"),
                lore = rewardSection.getStringList("lore"),
                displayRewards = rewardSection.getStringList("display-rewards"),
                iconAmount = rewardSection.getInt("icon-amount", 1).coerceAtLeast(1).coerceAtMost(64),
                requiredMinutes = requiredMinutes,
                commands = commands,
                items = items,
                icon = icon,
            )
        }.sortedBy { it.requiredMinutes }
    }

    private fun readItems(section: ConfigurationSection?, sourceName: String): List<ItemReward> {
        if (section == null) return emptyList()
        return section.getKeys(false).mapNotNull { key ->
            val itemSection = section.getConfigurationSection(key) ?: return@mapNotNull null
            val materialName = itemSection.getString("material").orEmpty()
            val material = Material.matchMaterial(materialName)
            if (material == null) {
                plugin.logger.severe("$sourceName.$key 缺少有效 material，已跳过该物品奖励。")
                return@mapNotNull null
            }
            ItemReward(
                material = material,
                amount = itemSection.getInt("amount", 1).coerceAtLeast(1),
                name = itemSection.getString("name"),
                lore = itemSection.getStringList("lore"),
            )
        }
    }

    private fun parseGuiRow(pattern: String, sourceName: String, rowNumber: Int): List<GuiLayoutCell> {
        val cells = mutableListOf<GuiLayoutCell>()
        var index = 0
        while (index < pattern.length && cells.size < 9) {
            val char = pattern[index]
            if (char == '`') {
                val end = pattern.indexOf('`', index + 1)
                if (end == -1) {
                    plugin.logger.severe("$sourceName GuiPlain 第 $rowNumber 行存在未闭合的奖励引用: $pattern")
                    break
                }
                val reference = pattern.substring(index + 1, end).trim()
                if (reference.isBlank()) {
                    plugin.logger.warning("$sourceName GuiPlain 第 $rowNumber 行存在空奖励引用，已忽略。")
                    cells += GuiLayoutCell()
                } else {
                    cells += GuiLayoutCell(rewardReference = reference)
                }
                index = end + 1
            } else {
                cells += GuiLayoutCell(key = char.takeUnless { it == ' ' })
                index += 1
            }
        }

        if (index < pattern.length) {
            plugin.logger.warning("$sourceName GuiPlain 第 $rowNumber 行超过 9 个槽位，超出部分已忽略。")
        }
        while (cells.size < 9) {
            cells += GuiLayoutCell()
        }
        return cells
    }

    private fun validateGuiRewardReferences() {
        RewardScope.entries.forEach { scope ->
            if (rewardScopeEnabledByScope[scope] != true) {
                return@forEach
            }
            val gui = guiConfig(scope)
            val rewards = rewards(scope)
            val references = guiRewardReferences(scope).distinct()
            references.forEach { reference ->
                if (resolveGuiReward(scope, reference) == null) {
                    plugin.logger.severe("${gui.sourceName} 引用奖励 '$reference'，但 ${rewardFiles.getValue(scope)} 没有加载到对应奖励。该 GUI 槽位不会显示。")
                }
            }
            if (rewards.isNotEmpty() && references.isEmpty() && !hasGenericRewardSlots(scope)) {
                plugin.logger.warning("${gui.sourceName} 没有任何奖励引用，也没有 IconFunction: reward 的通用奖励槽；${scope.key} 的奖励不会在 GUI 中显示。")
            }
        }
    }

    private fun applyPlaceholders(input: String, placeholders: Map<String, String>): String {
        var output = input
        placeholders.forEach { (key, value) -> output = output.replace("{$key}", value) }
        return output
    }
}

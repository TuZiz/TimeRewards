package ym.timeRewards.util

import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.inventory.ItemFlag
import java.util.regex.Matcher
import java.util.regex.Pattern

private val hashHexPattern = Pattern.compile("(?i)&?#([0-9a-f]{6})")
private val miniMessageHexPattern = Pattern.compile("(?i)<(?:color:)?#([0-9a-f]{6})>")
private val miniMessageColorClosePattern = Pattern.compile("(?i)</(?:color|#[0-9a-f]{6})>")
private val miniMessageResetPattern = Pattern.compile("(?i)<reset>")
private val miniMessageGradientPattern = Pattern.compile("(?i)<gradient:#([0-9a-f]{6}):#([0-9a-f]{6})>(.*?)</gradient>")

fun String.colorize(): String {
    val withMathPlaceholders = applyMathPlaceholders()
    val withGradients = miniMessageGradientPattern.matcher(withMathPlaceholders).replaceAll { match ->
        Matcher.quoteReplacement(applyGradient(match.group(3), match.group(1), match.group(2)))
    }
    val withMiniMessageHex = miniMessageHexPattern.matcher(withGradients).replaceAll { match ->
        Matcher.quoteReplacement(rgbColor(match.group(1)))
    }
    val withoutColorCloseTags = miniMessageColorClosePattern.matcher(withMiniMessageHex).replaceAll("")
    val withReset = miniMessageResetPattern.matcher(withoutColorCloseTags).replaceAll(ChatColor.RESET.toString())
    val withHashHex = hashHexPattern.matcher(withReset).replaceAll { match ->
        Matcher.quoteReplacement(rgbColor(match.group(1)))
    }

    return ChatColor.translateAlternateColorCodes('&', withHashHex)
}

private fun rgbColor(hex: String): String = net.md_5.bungee.api.ChatColor.of("#$hex").toString()

private fun applyGradient(input: String, fromHex: String, toHex: String): String {
    if (input.isEmpty()) {
        return input
    }

    val chars = input.toList()
    if (chars.size == 1) {
        return rgbColor(fromHex) + chars.first()
    }

    val from = parseRgb(fromHex)
    val to = parseRgb(toHex)
    return chars.mapIndexed { index, char ->
        val ratio = index.toDouble() / (chars.size - 1).toDouble()
        val red = interpolate(from[0], to[0], ratio)
        val green = interpolate(from[1], to[1], ratio)
        val blue = interpolate(from[2], to[2], ratio)
        "%s%s".format(rgbColor("%02x%02x%02x".format(red, green, blue)), char)
    }.joinToString("")
}

private fun parseRgb(hex: String): IntArray = intArrayOf(
    hex.substring(0, 2).toInt(16),
    hex.substring(2, 4).toInt(16),
    hex.substring(4, 6).toInt(16),
)

private fun interpolate(from: Int, to: Int, ratio: Double): Int = (from + ((to - from) * ratio)).toInt().coerceIn(0, 255)

fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return buildString {
        if (hours > 0) append("${hours}小时")
        if (minutes > 0 || hours > 0) append("${minutes}分钟")
        append("${seconds}秒")
    }
}

fun ItemStack.applyNameAndLore(name: String?, lore: List<String>): ItemStack {
    val meta = itemMeta ?: return this
    if (!name.isNullOrBlank()) {
        meta.setDisplayName(name.colorize())
    }
    if (lore.isNotEmpty()) {
        meta.lore = lore.map { it.colorize() }
    }
    itemMeta = meta
    return this
}

fun buildDisplayItem(
    material: Material,
    amount: Int,
    name: String,
    lore: List<String>,
    glow: Boolean,
    headOwner: Player? = null,
): ItemStack {
    val item = ItemStack(material, amount.coerceIn(1, 64))
    val meta = item.itemMeta ?: return item

    meta.setDisplayName(name.colorize())
    meta.lore = lore.map { it.colorize() }
    meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS)

    if (glow) {
        meta.addEnchant(Enchantment.UNBREAKING, 1, true)
    }
    if (meta is SkullMeta && headOwner != null) {
        meta.owningPlayer = headOwner
    }

    item.itemMeta = meta
    return item
}

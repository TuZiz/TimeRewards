package ym.timeRewards.data

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import ym.timeRewards.TimeRewards
import ym.timeRewards.model.RewardScope
import java.io.File
import java.util.UUID

class YamlPlayerDataStore(
    private val plugin: TimeRewards,
) : PlayerDataStore {
    private val storageFile = File(plugin.dataFolder, "playerdata.yml")

    override fun load(uuid: UUID, playerName: String): PlayerRewardProfile {
        ensureStorage()
        val yaml = YamlConfiguration.loadConfiguration(storageFile)
        val section = yaml.getConfigurationSection(uuid.toString())
        return readProfile(uuid, playerName, section)
    }

    override fun loadAll(): List<PlayerRewardProfile> {
        if (!storageFile.exists()) {
            return emptyList()
        }

        val yaml = YamlConfiguration.loadConfiguration(storageFile)
        return yaml.getKeys(false).mapNotNull { key ->
            val uuid = runCatching { UUID.fromString(key) }.getOrNull() ?: return@mapNotNull null
            val section = yaml.getConfigurationSection(key) ?: return@mapNotNull null
            readProfile(uuid, section.getString("name") ?: key, section)
        }
    }

    override fun save(profile: PlayerRewardProfile) {
        ensureStorage()
        val yaml = YamlConfiguration.loadConfiguration(storageFile)
        yaml.set(profile.uuid.toString(), null)
        val base = yaml.createSection(profile.uuid.toString())
        writeProfile(base, profile)
        yaml.save(storageFile)
    }

    private fun readProfile(uuid: UUID, playerName: String, section: ConfigurationSection?): PlayerRewardProfile {
        val profile = PlayerRewardProfile(
            uuid = uuid,
            lastKnownName = section?.getString("name") ?: playerName,
            autoClaimEnabled = section?.getBoolean("auto-claim", false) ?: false,
        )

        RewardScope.entries.forEach { scope ->
            val scopeSection = section?.getConfigurationSection(scope.key)
            profile.scopeData[scope] = ScopeProgress(
                token = scopeSection?.getString("token") ?: "",
                trackedSeconds = scopeSection?.getLong("seconds", 0L) ?: 0L,
                claimedRewardIds = scopeSection?.getStringList("claimed")?.toMutableSet() ?: mutableSetOf(),
            )
        }

        return profile
    }

    private fun writeProfile(base: ConfigurationSection, profile: PlayerRewardProfile) {
        base.set("name", profile.lastKnownName)
        base.set("auto-claim", profile.autoClaimEnabled)
        RewardScope.entries.forEach { scope ->
            val progress = profile.scopeData[scope] ?: ScopeProgress()
            val scopeSection = base.createSection(scope.key)
            scopeSection.set("token", progress.token)
            scopeSection.set("seconds", progress.trackedSeconds)
            scopeSection.set("claimed", progress.claimedRewardIds.toList().sorted())
        }
    }

    private fun ensureStorage() {
        if (storageFile.exists()) {
            return
        }
        storageFile.parentFile.mkdirs()
        storageFile.createNewFile()
    }
}

package ym.timeRewards.data

import ym.timeRewards.TimeRewards
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level

class PlayerDataService(
    private val plugin: TimeRewards,
) {
    private val profiles = ConcurrentHashMap<UUID, PlayerRewardProfile>()
    private val yamlStore = YamlPlayerDataStore(plugin)
    private var store: PlayerDataStore = createStore()

    fun load(uuid: UUID, playerName: String): PlayerRewardProfile {
        profiles[uuid]?.let {
            it.lastKnownName = playerName
            return it
        }

        val profile = store.load(uuid, playerName)
        profiles[uuid] = profile
        return profile
    }

    fun get(uuid: UUID): PlayerRewardProfile? = profiles[uuid]

    fun getOrLoad(uuid: UUID, playerName: String): PlayerRewardProfile = load(uuid, playerName)

    fun save(uuid: UUID): Boolean {
        val profile = profiles[uuid] ?: return true
        return saveProfile(profile)
    }

    fun unload(uuid: UUID) {
        profiles.remove(uuid)
    }

    fun saveAll() {
        profiles.values.forEach(::saveProfile)
    }

    fun reloadStorage() {
        saveAll()
        store.close()
        profiles.clear()
        store = createStore()
    }

    fun close() {
        profiles.clear()
        store.close()
    }

    private fun saveProfile(profile: PlayerRewardProfile): Boolean {
        try {
            store.save(profile)
            return true
        } catch (exception: Exception) {
            plugin.logger.log(Level.SEVERE, "Failed to save player data for ${profile.uuid}.", exception)
            return false
        }
    }

    private fun createStore(): PlayerDataStore {
        if (!plugin.config.getBoolean("database.enabled", false)) {
            plugin.logger.info("Using YAML player data storage.")
            return yamlStore
        }

        val type = plugin.config.getString("database.type", "mysql").orEmpty().lowercase()
        if (type != "mysql") {
            plugin.logger.warning("Unsupported database type '$type'. Falling back to YAML player data storage.")
            return yamlStore
        }

        return try {
            val mysqlStore = MysqlPlayerDataStore(readMysqlConfig())
            migrateExistingYaml(mysqlStore)
            plugin.logger.info("Using MySQL player data storage.")
            mysqlStore
        } catch (exception: Exception) {
            plugin.logger.log(Level.SEVERE, "Failed to initialize MySQL player data storage. Falling back to YAML.", exception)
            yamlStore
        }
    }

    private fun readMysqlConfig(): MysqlDatabaseConfig {
        val section = plugin.config.getConfigurationSection("database.mysql")
        return MysqlDatabaseConfig(
            host = section?.getString("host", "localhost") ?: "localhost",
            port = section?.getInt("port", 3306) ?: 3306,
            database = section?.getString("database", "timerewards") ?: "timerewards",
            username = section?.getString("username", "root") ?: "root",
            password = section?.getString("password", "") ?: "",
            parameters = section?.getString(
                "parameters",
                "useSSL=false&characterEncoding=utf8&serverTimezone=UTC",
            ) ?: "useSSL=false&characterEncoding=utf8&serverTimezone=UTC",
            tablePrefix = section?.getString("table-prefix", "timerewards_") ?: "timerewards_",
        )
    }

    private fun migrateExistingYaml(mysqlStore: MysqlPlayerDataStore) {
        if (!plugin.config.getBoolean("database.migrate-existing-yaml", true)) {
            return
        }

        val yamlProfiles = yamlStore.loadAll()
        if (yamlProfiles.isEmpty()) {
            plugin.logger.info("No YAML player data found for MySQL migration.")
            return
        }

        var merged = 0
        var failed = 0

        yamlProfiles.forEach { profile ->
            try {
                mysqlStore.save(profile)
                merged += 1
            } catch (exception: Exception) {
                failed += 1
                plugin.logger.log(Level.WARNING, "Failed to merge YAML player data for ${profile.uuid} into MySQL.", exception)
            }
        }

        plugin.logger.info("MySQL YAML merge finished. Merged $merged profiles${if (failed > 0) ", failed $failed profiles" else ""}.")
    }
}

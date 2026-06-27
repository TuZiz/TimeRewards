package ym.timeRewards.data

import ym.timeRewards.model.RewardScope
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

data class MysqlDatabaseConfig(
    val host: String,
    val port: Int,
    val database: String,
    val username: String,
    val password: String,
    val parameters: String,
    val tablePrefix: String,
) {
    val jdbcUrl: String
        get() {
            val query = parameters.trim().removePrefix("?")
            val base = "jdbc:mysql://$host:$port/$database"
            return if (query.isBlank()) base else "$base?$query"
        }
}

class MysqlPlayerDataStore(
    private val config: MysqlDatabaseConfig,
) : PlayerDataStore {
    private val tablePrefix = config.tablePrefix
        .filter { it.isLetterOrDigit() || it == '_' }
        .ifBlank { "timerewards_" }
    private val playersTable = table("players")
    private val progressTable = table("scope_progress")
    private val claimedTable = table("claimed_rewards")
    private val metaTable = table("meta")

    init {
        Class.forName("com.mysql.cj.jdbc.Driver")
        createTables()
    }

    @Synchronized
    override fun load(uuid: UUID, playerName: String): PlayerRewardProfile {
        connection().use { connection ->
            return loadProfile(connection, uuid, playerName)
        }
    }

    @Synchronized
    override fun save(profile: PlayerRewardProfile) {
        connection().use { connection ->
            connection.autoCommit = false
            try {
                val existing = loadProfile(connection, profile.uuid, profile.lastKnownName, lockForUpdate = true)
                val merged = PlayerProfileMergeSupport.mergePreferMax(profile, existing)
                saveProfile(connection, merged)
                connection.commit()
            } catch (exception: Exception) {
                connection.rollback()
                throw exception
            } finally {
                connection.autoCommit = true
            }
        }
    }

    private fun createTables() {
        connection().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS $playersTable (
                        uuid CHAR(36) NOT NULL,
                        name VARCHAR(32) NOT NULL,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        PRIMARY KEY (uuid)
                    ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
                    """.trimIndent(),
                )
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS $progressTable (
                        uuid CHAR(36) NOT NULL,
                        scope VARCHAR(16) NOT NULL,
                        token VARCHAR(64) NOT NULL,
                        tracked_seconds BIGINT NOT NULL DEFAULT 0,
                        PRIMARY KEY (uuid, scope)
                    ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
                    """.trimIndent(),
                )
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS $claimedTable (
                        uuid CHAR(36) NOT NULL,
                        scope VARCHAR(16) NOT NULL,
                        reward_id VARCHAR(191) NOT NULL,
                        PRIMARY KEY (uuid, scope, reward_id)
                    ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
                    """.trimIndent(),
                )
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS $metaTable (
                        meta_key VARCHAR(64) NOT NULL,
                        meta_value TEXT NOT NULL,
                        PRIMARY KEY (meta_key)
                    ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
                    """.trimIndent(),
                )
            }
        }
    }

    private fun saveProfile(connection: Connection, profile: PlayerRewardProfile) {
        connection.prepareStatement(
            """
            INSERT INTO $playersTable (uuid, name)
            VALUES (?, ?)
            ON DUPLICATE KEY UPDATE name = VALUES(name), updated_at = CURRENT_TIMESTAMP
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, profile.uuid.toString())
            statement.setString(2, profile.lastKnownName)
            statement.executeUpdate()
        }

        RewardScope.entries.forEach { scope ->
            val progress = profile.scopeData[scope] ?: ScopeProgress()
            connection.prepareStatement(
                """
                INSERT INTO $progressTable (uuid, scope, token, tracked_seconds)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE token = VALUES(token), tracked_seconds = VALUES(tracked_seconds)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, profile.uuid.toString())
                statement.setString(2, scope.key)
                statement.setString(3, progress.token)
                statement.setLong(4, progress.trackedSeconds)
                statement.executeUpdate()
            }

            connection.prepareStatement("DELETE FROM $claimedTable WHERE uuid = ? AND scope = ?").use { statement ->
                statement.setString(1, profile.uuid.toString())
                statement.setString(2, scope.key)
                statement.executeUpdate()
            }

            if (progress.claimedRewardIds.isNotEmpty()) {
                connection.prepareStatement(
                    """
                    INSERT IGNORE INTO $claimedTable (uuid, scope, reward_id)
                    VALUES (?, ?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    progress.claimedRewardIds.forEach { rewardId ->
                        statement.setString(1, profile.uuid.toString())
                        statement.setString(2, scope.key)
                        statement.setString(3, rewardId)
                        statement.addBatch()
                    }
                    statement.executeBatch()
                }
            }
        }
    }

    private fun loadProfile(
        connection: Connection,
        uuid: UUID,
        playerName: String,
        lockForUpdate: Boolean = false,
    ): PlayerRewardProfile {
        val profile = PlayerProfileMergeSupport.emptyProfile(uuid, playerName)
        val lockClause = if (lockForUpdate) " FOR UPDATE" else ""

        connection.prepareStatement("SELECT name FROM $playersTable WHERE uuid = ?$lockClause").use { statement ->
            statement.setString(1, uuid.toString())
            statement.executeQuery().use { result ->
                if (result.next() && profile.lastKnownName.isBlank()) {
                    profile.lastKnownName = result.getString("name")
                }
            }
        }

        connection.prepareStatement("SELECT scope, token, tracked_seconds FROM $progressTable WHERE uuid = ?$lockClause").use { statement ->
            statement.setString(1, uuid.toString())
            statement.executeQuery().use { result ->
                while (result.next()) {
                    val scope = RewardScope.fromKey(result.getString("scope")) ?: continue
                    profile.scopeData[scope] = ScopeProgress(
                        token = result.getString("token") ?: "",
                        trackedSeconds = result.getLong("tracked_seconds"),
                        claimedRewardIds = mutableSetOf(),
                    )
                }
            }
        }

        connection.prepareStatement("SELECT scope, reward_id FROM $claimedTable WHERE uuid = ?$lockClause").use { statement ->
            statement.setString(1, uuid.toString())
            statement.executeQuery().use { result ->
                while (result.next()) {
                    val scope = RewardScope.fromKey(result.getString("scope")) ?: continue
                    profile.scopeData.getOrPut(scope) { ScopeProgress() }.claimedRewardIds += result.getString("reward_id")
                }
            }
        }

        return profile
    }

    private fun connection(): Connection = DriverManager.getConnection(config.jdbcUrl, config.username, config.password)

    private fun table(name: String): String = "$tablePrefix$name"
}

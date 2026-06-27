package ym.timeRewards.data

import java.util.UUID

interface PlayerDataStore : AutoCloseable {
    fun load(uuid: UUID, playerName: String): PlayerRewardProfile

    fun save(profile: PlayerRewardProfile)

    fun loadAll(): List<PlayerRewardProfile> = emptyList()

    override fun close() {
    }
}

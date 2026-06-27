package ym.timeRewards.service

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import ym.timeRewards.TimeRewards
import ym.timeRewards.data.PlayerDataService
import ym.timeRewards.data.PlayerRewardProfile
import ym.timeRewards.data.ScopeProgress
import ym.timeRewards.model.RewardScope
import java.time.LocalDate
import java.util.EnumMap
import java.util.UUID

class TimeTrackingService(
    private val plugin: TimeRewards,
    private val playerDataService: PlayerDataService,
) {
    private val sessionStart = mutableMapOf<UUID, Long>()

    fun handleJoin(playerId: UUID) {
        sessionStart[playerId] = System.currentTimeMillis() / 1000L
        playerDataService.get(playerId)?.let(::normalize)
    }

    fun handleQuit(playerId: UUID): Boolean {
        val saved = flush(playerId)
        sessionStart.remove(playerId)
        return saved
    }

    fun flushOnline() {
        Bukkit.getOnlinePlayers().forEach { flush(it.uniqueId) }
    }

    fun currentMinutes(player: Player, scope: RewardScope): Long = currentSeconds(player, scope) / 60L

    fun currentSeconds(player: Player, scope: RewardScope): Long {
        val profile = playerDataService.getOrLoad(player.uniqueId, player.name)
        applySession(profile, player.uniqueId, mutateSessionStart = true)
        return profile.scopeData.getValue(scope).trackedSeconds
    }

    fun currentSeconds(profile: PlayerRewardProfile, scope: RewardScope): Long {
        val preview = copyProfile(profile)
        applySession(preview, profile.uuid, mutateSessionStart = false)
        return preview.scopeData.getValue(scope).trackedSeconds
    }

    fun flush(playerId: UUID): Boolean {
        val profile = playerDataService.get(playerId) ?: return true
        applySession(profile, playerId, mutateSessionStart = true)
        return playerDataService.save(playerId)
    }

    private fun applySession(profile: PlayerRewardProfile, playerId: UUID, mutateSessionStart: Boolean) {
        normalize(profile)
        val start = sessionStart[playerId] ?: return
        val now = System.currentTimeMillis() / 1000L
        if (now <= start) {
            return
        }
        val elapsed = now - start
        RewardScope.entries.forEach { scope ->
            profile.scopeData.getValue(scope).trackedSeconds += elapsed
        }
        if (mutateSessionStart) {
            sessionStart[playerId] = now
        }
    }

    private fun normalize(profile: PlayerRewardProfile) {
        val today = LocalDate.now()
        RewardScope.entries.forEach { scope ->
            val currentToken = scope.currentToken(today)
            val progress = profile.scopeData.getOrPut(scope) { ScopeProgress() }
            if (progress.token != currentToken) {
                if (scope != RewardScope.TOTAL) {
                    progress.trackedSeconds = 0L
                    progress.claimedRewardIds.clear()
                }
                progress.token = currentToken
            }
        }
    }

    private fun copyProfile(profile: PlayerRewardProfile): PlayerRewardProfile {
        val copy = PlayerRewardProfile(
            uuid = profile.uuid,
            lastKnownName = profile.lastKnownName,
            scopeData = EnumMap(RewardScope::class.java),
        )
        profile.scopeData.forEach { (scope, progress) ->
            copy.scopeData[scope] = ScopeProgress(progress.token, progress.trackedSeconds, progress.claimedRewardIds.toMutableSet())
        }
        return copy
    }
}

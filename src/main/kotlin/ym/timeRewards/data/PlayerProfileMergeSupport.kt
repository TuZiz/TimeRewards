package ym.timeRewards.data

import ym.timeRewards.model.RewardScope
import java.time.LocalDate
import java.util.EnumMap

internal object PlayerProfileMergeSupport {
    fun normalizedCopy(profile: PlayerRewardProfile, now: LocalDate = LocalDate.now()): PlayerRewardProfile {
        val copy = deepCopy(profile)
        normalizeInPlace(copy, now)
        return copy
    }

    fun mergePreferMax(
        incoming: PlayerRewardProfile,
        existing: PlayerRewardProfile? = null,
        now: LocalDate = LocalDate.now(),
    ): PlayerRewardProfile {
        val normalizedIncoming = normalizedCopy(incoming, now)
        val normalizedExisting = existing?.let { normalizedCopy(it, now) }
            ?: emptyProfile(normalizedIncoming.uuid, normalizedIncoming.lastKnownName, now)

        val merged = emptyProfile(
            uuid = normalizedIncoming.uuid,
            playerName = normalizedIncoming.lastKnownName.ifBlank { normalizedExisting.lastKnownName },
            now = now,
        )
        merged.autoClaimEnabled = normalizedIncoming.autoClaimEnabled

        RewardScope.entries.forEach { scope ->
            val incomingProgress = normalizedIncoming.scopeData.getOrPut(scope) { defaultProgress(scope, now) }
            val existingProgress = normalizedExisting.scopeData.getOrPut(scope) { defaultProgress(scope, now) }
            merged.scopeData[scope] = ScopeProgress(
                token = incomingProgress.token.ifBlank { existingProgress.token.ifBlank { scope.currentToken(now) } },
                trackedSeconds = maxOf(incomingProgress.trackedSeconds, existingProgress.trackedSeconds),
                claimedRewardIds = (existingProgress.claimedRewardIds + incomingProgress.claimedRewardIds).toMutableSet(),
            )
        }

        return merged
    }

    fun emptyProfile(
        uuid: java.util.UUID,
        playerName: String,
        now: LocalDate = LocalDate.now(),
    ): PlayerRewardProfile {
        val profile = PlayerRewardProfile(
            uuid = uuid,
            lastKnownName = playerName,
            autoClaimEnabled = false,
            scopeData = EnumMap(RewardScope::class.java),
        )
        RewardScope.entries.forEach { scope ->
            profile.scopeData[scope] = defaultProgress(scope, now)
        }
        return profile
    }

    private fun normalizeInPlace(profile: PlayerRewardProfile, now: LocalDate) {
        RewardScope.entries.forEach { scope ->
            val currentToken = scope.currentToken(now)
            val progress = profile.scopeData.getOrPut(scope) { defaultProgress(scope, now) }
            if (progress.token != currentToken) {
                if (scope != RewardScope.TOTAL) {
                    progress.trackedSeconds = 0L
                    progress.claimedRewardIds.clear()
                }
                progress.token = currentToken
            }
        }
    }

    private fun deepCopy(profile: PlayerRewardProfile): PlayerRewardProfile {
        val copy = PlayerRewardProfile(
            uuid = profile.uuid,
            lastKnownName = profile.lastKnownName,
            autoClaimEnabled = profile.autoClaimEnabled,
            scopeData = EnumMap(RewardScope::class.java),
        )
        profile.scopeData.forEach { (scope, progress) ->
            copy.scopeData[scope] = ScopeProgress(
                token = progress.token,
                trackedSeconds = progress.trackedSeconds,
                claimedRewardIds = progress.claimedRewardIds.toMutableSet(),
            )
        }
        return copy
    }

    private fun defaultProgress(scope: RewardScope, now: LocalDate): ScopeProgress {
        return ScopeProgress(token = scope.currentToken(now))
    }
}

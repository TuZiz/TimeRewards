package ym.timeRewards.data

import ym.timeRewards.model.RewardScope
import java.util.EnumMap
import java.util.UUID

data class PlayerRewardProfile(
    val uuid: UUID,
    var lastKnownName: String,
    val scopeData: MutableMap<RewardScope, ScopeProgress> = EnumMap(RewardScope::class.java),
)

data class ScopeProgress(
    var token: String = "",
    var trackedSeconds: Long = 0L,
    val claimedRewardIds: MutableSet<String> = mutableSetOf(),
)

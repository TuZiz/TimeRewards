package ym.timeRewards.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import ym.timeRewards.model.RewardScope
import java.time.LocalDate
import java.util.UUID

class PlayerProfileMergeSupportTest {
    @Test
    fun mergeUsesLargerTrackedSecondsAndUnionsClaims() {
        val now = LocalDate.of(2026, 4, 17)
        val uuid = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val existing = profile(uuid, "Keer", now).apply {
            scopeData.getValue(RewardScope.TODAY).trackedSeconds = 300L
            scopeData.getValue(RewardScope.TODAY).claimedRewardIds += "reward_a"
        }
        val incoming = profile(uuid, "Keer", now).apply {
            scopeData.getValue(RewardScope.TODAY).trackedSeconds = 420L
            scopeData.getValue(RewardScope.TODAY).claimedRewardIds += "reward_b"
        }

        val merged = PlayerProfileMergeSupport.mergePreferMax(incoming, existing, now)
        val progress = merged.scopeData.getValue(RewardScope.TODAY)

        assertEquals(420L, progress.trackedSeconds)
        assertEquals(now.toString(), progress.token)
        assertTrue("reward_a" in progress.claimedRewardIds)
        assertTrue("reward_b" in progress.claimedRewardIds)
    }

    @Test
    fun stalePeriodicYamlDataDoesNotOverrideCurrentPeriod() {
        val now = LocalDate.of(2026, 4, 17)
        val uuid = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val existing = profile(uuid, "Keer", now).apply {
            scopeData.getValue(RewardScope.TODAY).trackedSeconds = 600L
            scopeData.getValue(RewardScope.TODAY).claimedRewardIds += "today_reward"
        }
        val incoming = profile(uuid, "Keer", now.minusDays(1)).apply {
            scopeData.getValue(RewardScope.TODAY).trackedSeconds = 9999L
            scopeData.getValue(RewardScope.TODAY).claimedRewardIds += "yesterday_reward"
        }

        val merged = PlayerProfileMergeSupport.mergePreferMax(incoming, existing, now)
        val progress = merged.scopeData.getValue(RewardScope.TODAY)

        assertEquals(600L, progress.trackedSeconds)
        assertEquals(now.toString(), progress.token)
        assertTrue("today_reward" in progress.claimedRewardIds)
        assertFalse("yesterday_reward" in progress.claimedRewardIds)
    }

    @Test
    fun totalScopeStillKeepsLargestLifetimeValue() {
        val now = LocalDate.of(2026, 4, 17)
        val uuid = UUID.fromString("33333333-3333-3333-3333-333333333333")
        val existing = profile(uuid, "Keer", now).apply {
            scopeData.getValue(RewardScope.TOTAL).trackedSeconds = 3600L
        }
        val incoming = profile(uuid, "Keer", now.minusDays(7)).apply {
            scopeData.getValue(RewardScope.TOTAL).trackedSeconds = 5400L
        }

        val merged = PlayerProfileMergeSupport.mergePreferMax(incoming, existing, now)

        assertEquals(5400L, merged.scopeData.getValue(RewardScope.TOTAL).trackedSeconds)
        assertEquals("all-time", merged.scopeData.getValue(RewardScope.TOTAL).token)
    }

    private fun profile(uuid: UUID, playerName: String, now: LocalDate): PlayerRewardProfile {
        return PlayerProfileMergeSupport.emptyProfile(uuid, playerName, now)
    }
}

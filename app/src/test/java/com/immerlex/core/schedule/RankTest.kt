package com.immerlex.core.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RankTest {

    @Test
    fun anEmptyNotebookStillHasAName() {
        val rank = RankLadder.of(mastered = 0)

        assertEquals(RankTier.WHITE, rank.tier)
        assertEquals(20, rank.toNext)
    }

    @Test
    fun eachTierStartsWhereThePreviousOneStops() {
        for (tier in RankTier.entries) {
            assertEquals("到 ${tier.name} 的门槛", tier, RankLadder.of(tier.atLeastMastered).tier)
            if (tier != RankTier.WHITE) {
                assertEquals(
                    "门槛前一个数还不上这一档",
                    RankTier.entries[tier.ordinal - 1],
                    RankLadder.of(tier.atLeastMastered - 1).tier,
                )
            }
        }
    }

    @Test
    fun theLadderOnlyGoesUpAndKeepsItsOrder() {
        val thresholds = RankTier.entries.map { it.atLeastMastered }

        assertTrue("门槛必须一档比一档高", thresholds.zipWithNext().all { (a, b) -> b > a })
        assertEquals(
            listOf(1, 2, 3, 4, 5, 6, 1, 2, 3),
            RankTier.entries.dropLast(1).map { it.level },
        )
        assertEquals(6, RankTier.entries.count { it.kind == RankKind.LEVEL })
        assertEquals(3, RankTier.entries.count { it.kind == RankKind.DUAN })
        assertEquals(RankKind.SUMMIT, RankTier.SAGE.kind)
        assertEquals(3_500, RankTier.SAGE.atLeastMastered)
    }

    @Test
    fun theTopTierHasNowhereLeftToGo() {
        val rank = RankLadder.of(mastered = 9_999)

        assertEquals(RankTier.SAGE, rank.tier)
        assertNull("词圣上面不再排档", RankTier.SAGE.next)
        assertEquals(0, rank.toNext)
    }

    @Test
    fun toNextCountsDownToTheFollowingTier() {
        assertEquals(20, RankLadder.of(0).toNext)
        assertEquals(1, RankLadder.of(19).toNext)
        assertEquals(40, RankLadder.of(20).toNext)
    }
}

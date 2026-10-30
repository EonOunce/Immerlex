package com.immerlex.core.roommate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoommatePolicyTest {

    @Test
    fun earlyWordsPayDoubleAndTheCurveFlattensWithoutACap() {
        assertEquals(0, RewardPolicy.rewardFor(0))
        assertEquals(2, RewardPolicy.rewardFor(1))
        assertEquals(8, RewardPolicy.rewardFor(4))
        assertEquals(15, RewardPolicy.rewardFor(5))
        assertEquals(17, RewardPolicy.rewardFor(7))
    }

    @Test
    fun pastThirtyWordsEveryOtherWordStillPays() {
        assertEquals(0, RewardPolicy.rewardAt(31))
        assertEquals(1, RewardPolicy.rewardAt(32))
        assertEquals(40, RewardPolicy.rewardFor(30))
        assertEquals(40, RewardPolicy.rewardFor(31))
        assertEquals(41, RewardPolicy.rewardFor(32))
        assertEquals(275, RewardPolicy.rewardFor(500))
    }

    @Test
    fun dedupeKeysSeparateWordsDaysAndSpends() {
        assertTrue(RewardPolicy.wordKey(1, "apple") != RewardPolicy.wordKey(1, "banana"))
        assertTrue(RewardPolicy.wordKey(1, "apple") != RewardPolicy.wordKey(2, "apple"))
        assertTrue(RewardPolicy.milestoneKey(1) != RewardPolicy.wordKey(1, "apple"))
        assertEquals("buy:d1:g0", RewardPolicy.offerKey("d1:g0"))
        assertEquals("work:7", RewardPolicy.workKey(7))
    }

    @Test
    fun pricesComeFromTiersNotFromTheModel() {
        assertEquals(5, OfferPolicy.giftPrice("small"))
        assertEquals(12, OfferPolicy.giftPrice("medium"))
        assertEquals(25, OfferPolicy.giftPrice("large"))
        assertEquals(12, OfferPolicy.WORK_PRICE)
        assertEquals(12, OfferPolicy.RECIPE_PRICE)
        assertEquals(listOf(3, 8), OfferPolicy.STALL_PRICES)
    }

    @Test
    fun nearTargetIsAlwaysOneLadderStepAboveTheBalance() {
        assertEquals(3, OfferPolicy.nearPrice(0))
        assertEquals(5, OfferPolicy.nearPrice(3))
        assertEquals(5, OfferPolicy.nearPrice(4))
        assertEquals(8, OfferPolicy.nearPrice(5))
        assertEquals(200, OfferPolicy.nearPrice(199))
        assertEquals(200, OfferPolicy.nearPrice(1000))
        assertEquals(7, OfferPolicy.nearGap(12, 5))
        assertEquals(0, OfferPolicy.nearGap(12, 12))
        assertEquals(0, OfferPolicy.nearGap(12, 20))
    }

    @Test
    fun petGiftsAreBlockedOnTheFirstDayAndTooCloseTogether() {
        assertTrue(PetGiftPolicy.blocked(today = 100, creationDay = 100, lastGiftDay = null, giftsLast7Days = 0))
        assertTrue(PetGiftPolicy.blocked(today = 104, creationDay = 100, lastGiftDay = 102, giftsLast7Days = 1))
        assertTrue(PetGiftPolicy.blocked(today = 104, creationDay = 100, lastGiftDay = 101, giftsLast7Days = 2))
        assertFalse(PetGiftPolicy.blocked(today = 103, creationDay = 100, lastGiftDay = 100, giftsLast7Days = 1))
        assertFalse(PetGiftPolicy.blocked(today = 110, creationDay = 100, lastGiftDay = 104, giftsLast7Days = 1))
    }

    @Test
    fun proactivePetGiftsPreferAbsenceThenStreakThenWhim() {
        val base = PetGiftPolicy.proactiveCause(
            today = 120,
            creationDay = 100,
            lastVisitDay = 110,
            streak = 7,
            lastGiftDay = null,
            giftsLast7Days = 0,
            whimRoll = 0,
        )
        assertEquals("absence", base)

        val streak = PetGiftPolicy.proactiveCause(
            today = 120,
            creationDay = 100,
            lastVisitDay = 119,
            streak = 7,
            lastGiftDay = null,
            giftsLast7Days = 0,
            whimRoll = 0,
        )
        assertEquals("streak", streak)

        val whim = PetGiftPolicy.proactiveCause(
            today = 120,
            creationDay = 100,
            lastVisitDay = 119,
            streak = 3,
            lastGiftDay = null,
            giftsLast7Days = 0,
            whimRoll = 0,
        )
        assertEquals("whim", whim)

        val quiet = PetGiftPolicy.proactiveCause(
            today = 120,
            creationDay = 100,
            lastVisitDay = 119,
            streak = 3,
            lastGiftDay = null,
            giftsLast7Days = 0,
            whimRoll = 2,
        )
        assertNull(quiet)
        assertNull(
            PetGiftPolicy.proactiveCause(
                today = 120,
                creationDay = 100,
                lastVisitDay = 110,
                streak = 7,
                lastGiftDay = 118,
                giftsLast7Days = 1,
                whimRoll = 0,
            ),
        )
    }

    @Test
    fun responseGiftComesOnceThenOnlyAfterThreeDays() {
        assertEquals(
            "first",
            PetGiftPolicy.responseCause(
                today = 120,
                creationDay = 100,
                hasEverGifted = false,
                lastGiftDay = null,
                giftsLast7Days = 0,
            ),
        )
        assertEquals(
            "return",
            PetGiftPolicy.responseCause(
                today = 120,
                creationDay = 100,
                hasEverGifted = true,
                lastGiftDay = 116,
                giftsLast7Days = 1,
            ),
        )
        assertNull(
            PetGiftPolicy.responseCause(
                today = 120,
                creationDay = 100,
                hasEverGifted = true,
                lastGiftDay = 118,
                giftsLast7Days = 1,
            ),
        )
        assertNull(
            PetGiftPolicy.responseCause(
                today = 120,
                creationDay = 120,
                hasEverGifted = false,
                lastGiftDay = null,
                giftsLast7Days = 0,
            ),
        )
    }

    @Test
    fun whimRollIsStableWithinTheDayAndCoversFourDays() {
        assertEquals(PetGiftPolicy.whimRoll(120), PetGiftPolicy.whimRoll(120))
        val rolls = (120L..123L).map { PetGiftPolicy.whimRoll(it) }.toSet()
        assertEquals(4, rolls.size)
        assertTrue(rolls.all { it in 0..3 })
    }

    @Test
    fun habitNeedsThreeDistinctDaysInsideTheWindow() {
        val today = 1000L
        assertFalse(HabitPolicy.shouldLearn(listOf(today, today - 3), today, alreadyLearned = false))
        assertTrue(HabitPolicy.shouldLearn(listOf(today, today - 3, today - 6), today, alreadyLearned = false))
    }

    @Test
    fun daysOlderThanTheWindowDoNotCount() {
        val today = 1000L
        assertFalse(
            HabitPolicy.shouldLearn(
                listOf(today, today - 13, today - 14),
                today,
                alreadyLearned = false,
            ),
        )
    }

    @Test
    fun theWindowIncludesExactlyFourteenDays() {
        val today = 1000L
        assertTrue(
            HabitPolicy.shouldLearn(
                listOf(today, today - 7, today - 13),
                today,
                alreadyLearned = false,
            ),
        )
    }

    @Test
    fun anAlreadyLearnedHabitStaysLearned() {
        val today = 1000L
        assertFalse(HabitPolicy.shouldLearn(listOf(today, today - 1, today - 2), today, alreadyLearned = true))
    }

    @Test
    fun habitLabelsAreTightenedBeforeTheyCount() {
        assertEquals("擦窗台", HabitPolicy.normalize(" 擦 窗 台 "))
        assertNull(HabitPolicy.normalize(""))
        assertNull(HabitPolicy.normalize("它今天很开心地擦了一下午的窗台"))
        assertNull(HabitPolicy.normalize("擦窗台，顺便"))
        assertNull(HabitPolicy.normalize("aha!"))
    }
}

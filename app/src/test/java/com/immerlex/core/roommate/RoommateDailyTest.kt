package com.immerlex.core.roommate

import com.immerlex.core.db.RM_CHANNEL_GIFT
import com.immerlex.core.db.RM_CHANNEL_NEAR
import com.immerlex.core.db.RM_CHANNEL_RECIPE
import com.immerlex.core.db.RM_CHANNEL_STALL
import com.immerlex.core.db.RM_PACK_LOCAL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoommateDailyTest {

    private val day = 20_300L

    @Test
    fun localPackCarriesEverySectionWithoutTheModel() {
        val pack = RoommateText.dailyPack(day, "昨天没读完的信", 18)

        assertEquals(3, pack.gifts.size)
        assertTrue(pack.gifts.all { it.tier in setOf("small", "medium", "large") })
        assertEquals(2, pack.stall.size)
        assertTrue(pack.coquetry.size >= 3)
        assertNotNull(pack.petGift)
        assertNotNull(pack.near)
        assertTrue(pack.episode.line.isNotBlank())
        assertTrue(pack.episode.choiceA.isNotBlank())
        assertTrue(pack.episode.choiceB.isNotBlank())

        val recipe = pack.recipe
        assertNotNull(recipe)
        val indices = recipe!!.giftIndices
        assertTrue(indices.size in 2..3)
        assertEquals(indices.size, indices.distinct().size)
        assertTrue(indices.all { it in 0..2 })
    }

    @Test
    fun offersPinIdsPricesAndTheHiddenRecipe() {
        val pack = RoommateText.dailyPack(day, null, 18)
        val rows = packOffers(pack, day, nearPrice = 18, atSec = 42, source = RM_PACK_LOCAL)

        assertEquals(3, rows.count { it.channel == RM_CHANNEL_GIFT })
        assertEquals(2, rows.count { it.channel == RM_CHANNEL_STALL })
        assertEquals(1, rows.count { it.channel == RM_CHANNEL_RECIPE })
        assertEquals(1, rows.count { it.channel == RM_CHANNEL_NEAR })

        assertEquals(listOf("d$day:g0", "d$day:g1", "d$day:g2"), rows.filter { it.channel == RM_CHANNEL_GIFT }.map { it.offerId })
        assertEquals(5, rows.first { it.offerId == "d$day:g0" }.price)
        assertEquals(12, rows.first { it.offerId == "d$day:g1" }.price)
        assertEquals(25, rows.first { it.offerId == "d$day:g2" }.price)
        assertEquals(listOf(3, 8), rows.filter { it.channel == RM_CHANNEL_STALL }.map { it.price })

        val recipe = rows.first { it.channel == RM_CHANNEL_RECIPE }
        assertEquals("d$day:r", recipe.offerId)
        assertTrue(recipe.hidden)
        assertEquals(12, recipe.price)
        assertEquals(pack.recipe!!.giftIndices.joinToString(",") { "d$day:g$it" }, recipe.requires)

        val near = rows.first { it.channel == RM_CHANNEL_NEAR }
        assertEquals("near:$day", near.offerId)
        assertEquals(18, near.price)
        assertFalse(near.hidden)
    }

    @Test
    fun giftsAtTheSamePriceBandKeepTheirContentButNeverThePrice() {
        val cheap = RoommateText.dailyPack(day, null, 3).near
        val medium = RoommateText.dailyPack(day, null, 35).near
        val high = RoommateText.dailyPack(day, null, 200).near
        assertTrue(cheap!!.name.isNotBlank())
        assertTrue(medium!!.name.isNotBlank())
        assertTrue(high!!.name.isNotBlank())
    }

    @Test
    fun linebookHasSixDeterministicLinesPerAction() {
        val book = RoommateText.linebook()
        RoomAction.entries.forEach { action ->
            val lines = book[action]
            assertEquals(6, lines?.size)
            assertTrue(lines!!.all { it.isNotBlank() })
        }
    }

    @Test
    fun petGiftContentStaysStablePerDay() {
        val first = RoommateText.petGift(day)
        val second = RoommateText.petGift(day)
        assertEquals(first, second)
        assertEquals(first.name, RoommateText.petGift(day + 6).name)
        assertTrue(first.story.isNotBlank())
        assertTrue(first.line.isNotBlank())
    }
}

package com.immerlex.core.roommate

import com.immerlex.core.db.RoommateAssetEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoommateAssetsTest {

    private fun asset(id: String, tag: String) =
        RoommateAssetEntity(assetId = id, fileName = "$id.gif", tag = tag, bytes = 1, importedAtSec = 0)

    @Test
    fun onlyGifFilesAreImported() {
        assertTrue(RoommateAssets.isGif("米提亚_眨眼睛.gif"))
        assertTrue(RoommateAssets.isGif("A.GIF"))
        assertFalse(RoommateAssets.isGif("说明.txt"))
        assertFalse(RoommateAssets.isGif("gif"))
    }

    @Test
    fun tagDropsTheCharacterNameAndTheDate() {
        assertEquals("吹鼻子", RoommateAssets.tagOf("米提亚_吹鼻子_2026-10-30-00-18-07.gif"))
        assertEquals("摇铃", RoommateAssets.tagOf("米提亚_摇铃_2026-10-30-00-18-07.gif"))
        assertEquals("生气", RoommateAssets.tagOf("米提亚_生气_2026-10-30-00-18-07.gif"))
    }

    @Test
    fun tagKeepsOnlyTheFirstActionWhenTheNameHasSeveralWords() {
        assertEquals("通知", RoommateAssets.tagOf("米提亚_通知_提示 星星_收藏_2026-10-30-00-18-07.gif"))
    }

    @Test
    fun trailingCountersDoNotBecomePartOfTheTag() {
        assertEquals("咬", RoommateAssets.tagOf("米提亚_咬 1_2026-10-30-00-18-07.gif"))
    }

    @Test
    fun aNameWithoutTheCharacterPrefixIsKeptWhole() {
        assertEquals("眨眼", RoommateAssets.tagOf("眨眼.gif"))
    }

    @Test
    fun unreadableNamesFallBackToOther() {
        assertEquals("其他", RoommateAssets.tagOf("2026-10-30-00-18-07.gif"))
        assertEquals("其他", RoommateAssets.tagOf(".gif"))
    }

    @Test
    fun pickPrefersMatchingTagsAndIsDeterministic() {
        val assets = listOf(asset("a", "吹鼻子"), asset("b", "摇铃"), asset("c", "生气"))
        val first = RoommateAssets.pick(assets, RoomAction.TOUCH, seed = 42)
        val again = RoommateAssets.pick(assets, RoomAction.TOUCH, seed = 42)
        assertEquals(first?.assetId, again?.assetId)
        assertTrue(RoomAction.TOUCH.keywords.any { first!!.tag.contains(it) })
    }

    @Test
    fun pickFallsBackToAnyAssetWhenNoTagMatches() {
        val assets = listOf(asset("a", "生气"), asset("b", "其他"))
        assertTrue(RoommateAssets.pick(assets, RoomAction.OUTING, seed = 7) != null)
    }

    @Test
    fun withoutAssetsThereIsNothingToDraw() {
        assertNull(RoommateAssets.pick(emptyList(), RoomAction.TALK, seed = 1))
        assertNull(RoommateAssets.next(emptyList(), RoomAction.TALK, cursor = 0, onStage = null))
    }

    @Test
    fun nextNeverRepeatsWhatIsAlreadyOnStage() {
        val assets = listOf(asset("a", "吹鼻子"), asset("b", "摇铃"), asset("c", "眨眼"))
        val picked = RoommateAssets.next(assets, RoomAction.TALK, cursor = 0, onStage = "a")
        assertTrue(picked?.assetId != "a")
    }

    @Test
    fun nextKeepsPlayingTheOnlyAssetItHas() {
        val assets = listOf(asset("a", "吹鼻子"))
        assertEquals("a", RoommateAssets.next(assets, RoomAction.TOUCH, cursor = 3, onStage = "a")?.assetId)
    }

    @Test
    fun nextWalksThroughTheWholePool() {
        val assets = listOf(asset("a", "吹鼻子"), asset("b", "摇铃"), asset("c", "眨眼"))
        val seen = (0..5).mapNotNull {
            RoommateAssets.next(assets, null, cursor = it, onStage = null)?.assetId
        }.toSet()
        assertEquals(setOf("a", "b", "c"), seen)
    }

    @Test
    fun nextPrefersTagsThatMatchTheAction() {
        val assets = listOf(asset("a", "吹鼻子"), asset("b", "生气"), asset("c", "眨眼"))
        val picked = RoommateAssets.next(assets, RoomAction.TALK, cursor = 0, onStage = null)
        assertTrue(RoomAction.TALK.keywords.any { picked!!.tag.contains(it) })
    }
}

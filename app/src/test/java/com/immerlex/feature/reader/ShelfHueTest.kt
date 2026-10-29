package com.immerlex.feature.reader

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 题材色只在"色相是 0–360 度"时才有意义：`Color.hsl` 吃度数，一旦有人把结果再除以 360，
 * 所有书都会落到 0 度附近，整面书架塌成同一种酱色。
 */
class ShelfHueTest {

    @Test
    fun everyGenreGetsItsOwnSpotOnTheWheel() {
        val genres = listOf("科幻", "宫斗", "灯下的信", "修仙", "校园")
        val hues = genres.map(::shelfHue)

        hues.forEach { assertTrue("色相必须落在 0–360 度：$it", it >= 0f && it < 360f) }
        assertTrue("题材之间要拉得开，否则分色没有意义：$hues", hues.max() - hues.min() > 60f)
        assertTrue("同一本书每次必须是同一个色", hues == genres.map(::shelfHue))
    }
}

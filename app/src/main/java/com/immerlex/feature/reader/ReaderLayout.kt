package com.immerlex.feature.reader

import com.immerlex.app.ui.WindowClass
import com.immerlex.app.ui.windowClassOf

/**
 * 阅读页在某个宽度下摆几栏：目录 / 正文 / 词卡。
 *
 * 手机上还是原来的样子——词卡是底部抽屉，目录是标题旁边的下拉；平板（≥600dp）先让
 * 目录单独成栏，更宽（≥1200dp）词卡再挪到右边，不再盖住正文。
 */
data class ReaderPanes(val chapters: Boolean, val card: Boolean)

fun readerPanesOf(widthDp: Int): ReaderPanes = when (windowClassOf(widthDp)) {
    WindowClass.COMPACT -> ReaderPanes(chapters = false, card = false)
    WindowClass.MEDIUM, WindowClass.EXPANDED -> ReaderPanes(chapters = true, card = false)
    WindowClass.WIDE -> ReaderPanes(chapters = true, card = true)
}

/** 正文栏最宽到这里：再宽一行就装得下八九十个字，眼睛找不到下一行的开头。 */
const val READER_BODY_MAX_WIDTH_DP = 720

const val READER_CHAPTER_RAIL_DP = 224

const val READER_CARD_PANE_DP = 344

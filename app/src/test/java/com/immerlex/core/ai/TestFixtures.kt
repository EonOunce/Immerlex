package com.immerlex.core.ai

import com.immerlex.core.bible.CharacterChange
import kotlinx.serialization.json.Json

internal const val BLOCK = "山雾还没有散她提着灯走过长长的回廊脚步声在石板上回响起一层又一层又一层"

internal val blockHanLength = BLOCK.count { it in '一'..'鿿' }

internal data class Fixture(val doc: ChapterDoc, val words: List<String>)

/** paragraphs x (blocks of Han text + wordsPerParagraph English words). */
internal fun fixture(
    paragraphs: Int,
    blocks: Int,
    wordsPerParagraph: Int,
    title: String = "第一章 · 起雾",
    summary: String = "她走进雾里。",
    characters: List<String> = emptyList(),
    changes: List<CharacterChange> = emptyList(),
): Fixture {
    val words = mutableListOf<String>()
    val body = (0 until paragraphs).map { p ->
        val segments = mutableListOf(RawSegment(k = "t", v = BLOCK.repeat(blocks)))
        repeat(wordsPerParagraph) { w ->
            val en = "w${p}_$w"
            words += en
            segments += RawSegment(
                k = "w", id = "$p$w", en = en, zh = "义$p$w",
                ipa = "/$en/", st = "new", def = "v. 释义", ex = "example",
            )
        }
        segments
    }
    return Fixture(
        ChapterDoc(
            title = title, summary = summary, characters = characters,
            changes = changes.map { ChangeDoc(it.who, it.what) }, paragraphs = body,
        ),
        words,
    )
}

internal fun encode(doc: ChapterDoc): String = Json.encodeToString(ChapterDoc.serializer(), doc)

internal fun parsed(fixture: Fixture): ParsedChapter? = ChapterParser.parse(encode(fixture.doc))

package com.immerlex.core.reader

import com.immerlex.core.ai.RawSegment
import com.immerlex.core.ai.ChapterDoc
import com.immerlex.core.ai.ChapterParser
import com.immerlex.core.db.WordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun bank() = listOf(
    WordEntity(
        id = 7,
        word = "resist",
        phonetic = "/rɪˈzɪst/",
        translation = "忍住",
        definition = "v. 抵挡",
        source = "test",
        createdAtSec = 0,
    ),
)

private fun chapter(modelGloss: String, modelIpa: String) = ChapterParser.parse(
    ChapterDoc(
        title = "T",
        summary = "",
        paragraphs = listOf(
            listOf(
                RawSegment(k = "t", v = "刘禧终究 "),
                RawSegment(k = "w", en = "RESIST", zh = modelGloss, ipa = modelIpa, st = "new", def = "", ex = "example"),
                RawSegment(k = "t", v = " 没有回头。"),
                RawSegment(k = "w", en = "ghost", zh = "幽灵", ipa = "", st = "new", def = "n. 幽灵", ex = ""),
            ),
        ),
    ).let { kotlinx.serialization.json.Json.encodeToString(ChapterDoc.serializer(), it) },
)!!

class ChapterResolverTest {

    @Test
    fun joinsBankWordsCaseInsensitively() {
        val resolved = ChapterResolver.resolve(chapter("忍住", ""), ChapterResolver.bankIndex(bank()))
        val words = resolved.flatten().filterIsInstance<ReaderSegment.Word>().map { it.word }

        assertEquals(2, words.size)
        assertEquals(7L, words[0].wordId)
        assertTrue(words[0].inBank)
        assertNull(words[1].wordId)
        assertFalse(words[1].inBank)
    }

    @Test
    fun prefersModelContextualGlossAndFallsBackToBank() {
        val withModel = ChapterResolver.resolve(chapter("抗拒", "/x/"), ChapterResolver.bankIndex(bank()))
            .flatten().filterIsInstance<ReaderSegment.Word>().first().word
        val fromBank = ChapterResolver.resolve(chapter("", ""), ChapterResolver.bankIndex(bank()))
            .flatten().filterIsInstance<ReaderSegment.Word>().first().word

        assertEquals("抗拒", withModel.zh)
        assertEquals("/x/", withModel.ipa)
        assertEquals("忍住", fromBank.zh)
        assertEquals("/rɪˈzɪst/", fromBank.ipa)
        assertEquals("v. 抵挡", fromBank.def)
    }

    @Test
    fun keepsTextSegmentsInOrder() {
        val resolved = ChapterResolver.resolve(chapter("忍住", ""), ChapterResolver.bankIndex(bank()))
        val kinds = resolved.single().map { if (it is ReaderSegment.Text) "t" else "w" }

        assertEquals(listOf("t", "w", "t", "w"), kinds)
        assertEquals("刘禧终究 ", (resolved.single()[0] as ReaderSegment.Text).value)
    }
}

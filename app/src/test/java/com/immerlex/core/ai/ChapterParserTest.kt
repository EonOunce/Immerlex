package com.immerlex.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterParserTest {

    @Test
    fun extractsObjectFromFencedResponse() {
        val raw = "好的，这是结果：\n```json\n{\"title\":\"T\",\"paragraphs\":[[{\"k\":\"t\",\"v\":\"汉字\"}]]}\n```"
        val chapter = ChapterParser.parse(raw)
        assertNotNull(chapter)
        assertEquals("T", chapter!!.title)
        assertEquals(1, chapter.paragraphs.size)
    }

    @Test
    fun ignoresUnknownKeysAndBadSegmentKind() {
        val chapter = ChapterParser.parse("""{"title":"T","extra":1,"paragraphs":[[{"k":"t","v":"雾"},{"k":"z","v":"x"}]]}""")
        assertEquals(1, chapter!!.paragraphs.single().size)
        assertTrue(chapter.paragraphs.single().single() is Segment.Text)
    }

    @Test
    fun returnsNullWhenNoJsonObject() {
        assertNull(ChapterParser.parse("对不起，我无法生成。"))
        assertNull(ChapterParser.parse("{not json}"))
    }

    @Test
    fun keepsCastTrimmedAndDeduplicated() {
        val chapter = ChapterParser.parse(
            """{"title":"T","summary":"S","characters":[" 落薇 · 皇后 ","刘禧 · 近侍","","落薇 · 皇后"],
               "paragraphs":[[{"k":"t","v":"雾"}]]}""",
        )
        assertEquals(listOf("落薇 · 皇后", "刘禧 · 近侍"), chapter!!.characters)
    }

    @Test
    fun missingCastDefaultsToEmpty() {
        val chapter = ChapterParser.parse("""{"title":"T","paragraphs":[[{"k":"t","v":"雾"}]]}""")
        assertEquals(emptyList<String>(), chapter!!.characters)
    }

    @Test
    fun countsOnlyHanCharacters() {
        val f = fixture(paragraphs = 3, blocks = 2, wordsPerParagraph = 1)
        assertEquals(3 * 2 * blockHanLength, ChapterValidator.countHan(parsed(f)!!))
    }

    @Test
    fun acceptsChapterInsideDenseTier() {
        val f = fixture(paragraphs = 6, blocks = 2, wordsPerParagraph = 3)
        val issues = ChapterValidator.validate(parsed(f), f.words, Density.DENSE, minHanChars = 400)
        assertEquals(emptyList<String>(), issues.map { it.code })
    }

    @Test
    fun flagsSparseDensityAgainstDenseTier() {
        val f = fixture(paragraphs = 10, blocks = 3, wordsPerParagraph = 1)
        val issues = ChapterValidator.validate(parsed(f), f.words, Density.DENSE, minHanChars = 400)
        assertEquals(listOf("density_low"), issues.map { it.code })
    }

    @Test
    fun flagsTooShort() {
        val f = fixture(paragraphs = 1, blocks = 1, wordsPerParagraph = 2)
        val issues = ChapterValidator.validate(parsed(f), f.words, Density.DENSE, minHanChars = 400)
        assertTrue(issues.any { it.code == "too_short" })
    }

    @Test
    fun flagsWordsThatWereNotRequested() {
        val f = fixture(paragraphs = 2, blocks = 2, wordsPerParagraph = 3)
        val issues = ChapterValidator.validate(parsed(f), listOf("totally", "other"), Density.DENSE, minHanChars = 50)
        assertEquals(6, issues.count { it.code == "word_not_requested" })
        assertEquals(6, issues.size)
    }

    @Test
    fun flagsMissingGlossAndRepeatedWord() {
        val word = { gloss: String, def: String ->
            RawSegment(k = "w", en = "word0", zh = gloss, ipa = "/w/", st = "new", def = def, ex = "ex")
        }
        val paragraphs = listOf(
            listOf(
                RawSegment(k = "t", v = BLOCK.repeat(3)),
                word("", ""),
                word("重复", "v. 释义"),
                word("重复", "v. 释义"),
                word("重复", "v. 释义"),
                word("重复", "v. 释义"),
            ),
        )
        val chapter = ChapterParser.parse(encode(ChapterDoc(title = "T", summary = "", paragraphs = paragraphs)))
        val issues = ChapterValidator.validate(chapter, listOf("word0"), Density.DENSE, minHanChars = 50)
        assertEquals(listOf("word_overused", "missing_gloss"), issues.map { it.code })
    }

    @Test
    fun reportsUnparseableAsSingleIssue() {
        val issues = ChapterValidator.validate(null, listOf("a"), Density.MEDIUM, minHanChars = 100)
        assertEquals(listOf("unparseable_json"), issues.map { it.code })
    }
}

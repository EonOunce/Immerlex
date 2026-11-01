package com.immerlex.core.quiz

import com.immerlex.core.db.WordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionBuildersTest {

    private val harbour = word(1, "harbour", "港口", def = "a place where ships stay")
    private val balcony = word(2, "balcony", "阳台")
    private val lantern = word(3, "lantern", "灯笼")
    private val pebble = word(4, "pebble", "鹅卵石")
    private val pool = listOf(harbour, balcony, lantern, pebble)

    @Test
    fun enToZhAsksForTheChineseMeaningWithDistinctDistractors() {
        val question = EnToZhBuilder().build(harbour, QuestionContext(pool = pool, seed = 1))!!

        assertEquals(QuizType.EN_ZH, question.type)
        assertEquals("harbour", question.prompt)
        assertEquals(4, question.options.size)
        assertEquals(1, question.options.count { it.correct })
        assertEquals("港口", question.correctText())
        assertTrue(question.options.all { it.gloss.isNotBlank() })
        // 蒙错的那个中文选项，答完要能看出它其实是哪个单词。
        assertEquals("harbour", question.correctGloss())
    }

    @Test
    fun aWordNeedsAtLeastOneDistractorToBecomeAChoiceQuestion() {
        assertNull(EnToZhBuilder().build(harbour, QuestionContext(pool = listOf(harbour))))
        assertNull(ZhToEnBuilder().build(harbour, QuestionContext(pool = listOf(harbour))))
    }

    @Test
    fun zhToEnAsksForTheEnglishWordAndShowsGlosses() {
        val question = ZhToEnBuilder().build(harbour, QuestionContext(pool = pool, seed = 2))!!

        assertEquals(QuizType.ZH_EN, question.type)
        assertEquals("港口", question.prompt)
        assertEquals("harbour", question.correctText())
        assertTrue("英文选项底下要能对出中文释义", question.options.all { it.gloss.isNotBlank() })
        assertEquals("港口", question.correctGloss())
    }

    @Test
    fun clozeUsesTheChapterSentenceWhenThereIsOne() {
        val context = QuestionContext(
            pool = pool,
            sentences = mapOf("harbour" to listOf("雨停之后，她走到____边。")),
            seed = 3,
        )

        val question = ClozeBuilder().build(harbour, context)!!

        assertEquals(QuizType.CLOZE, question.type)
        assertEquals("雨停之后，她走到____边。", question.prompt)
        assertEquals("harbour", question.correctText())
    }

    @Test
    fun clozeFallsBackToAnExampleProvidedByTheEnricher() {
        val context = QuestionContext(
            pool = pool,
            enrichment = QuizEnrichment(mapOf(1L to EnrichedWord(example = "The harbour is quiet."))),
            seed = 4,
        )

        val question = ClozeBuilder().build(harbour, context)!!

        assertTrue(question.prompt.contains(ClozeSentences.BLANK))
        assertFalse(question.prompt.contains("harbour"))
    }

    @Test
    fun clozeIsUnavailableWithoutASentenceOrExample() {
        assertNull(ClozeBuilder().build(harbour, QuestionContext(pool = pool)))
    }

    @Test
    fun definitionUsesTheBankDefinitionAndFallsBackToTheEnricher() {
        val fromBank = DefinitionBuilder().build(harbour, QuestionContext(pool = pool, seed = 5))!!
        assertEquals("a place where ships stay", fromBank.prompt)

        val bare = word(5, "quay", "码头")
        assertNull(DefinitionBuilder().build(bare, QuestionContext(pool = pool)))
        val enriched = DefinitionBuilder().build(
            bare,
            QuestionContext(
                pool = pool,
                enrichment = QuizEnrichment(mapOf(5L to EnrichedWord(definition = "a landing place"))),
                seed = 6,
            ),
        )!!
        assertEquals("a landing place", enriched.prompt)
    }

    @Test
    fun extraDistractorsFromTheEnricherJoinTheOptionPool() {
        val context = QuestionContext(
            pool = listOf(harbour),
            enrichment = QuizEnrichment(
                mapOf(1L to EnrichedWord(distractors = listOf(EnrichedDistractor("dock", "码头")))),
            ),
            seed = 7,
        )

        val question = EnToZhBuilder().build(harbour, context)!!
        assertTrue(question.options.any { it.text == "码头" })
        assertNotNull(question.options.firstOrNull { it.correct })
    }

    @Test
    fun aMarkedConfusableStaysEvenWhenThePoolRotates() {
        val context = QuestionContext(
            pool = pool,
            enrichment = QuizEnrichment(mapOf(1L to EnrichedWord(distractors = aiPool))),
            confusables = mapOf(1L to listOf(EnrichedDistractor("harbor", "港口码头"))),
        )
        (0..4).forEach { attempt ->
            val question = EnToZhBuilder().build(harbour, context.copy(attempts = mapOf(1L to attempt), seed = 9L))!!
            assertTrue("第 ${attempt + 1} 次也该看到用户标的易混词", question.options.any { it.text == "港口码头" })
        }
    }

    @Test
    fun theSameWordShowsADifferentOptionSetEachTimeItIsAsked() {
        val context = QuestionContext(
            pool = emptyList(),
            enrichment = QuizEnrichment(mapOf(1L to EnrichedWord(distractors = aiPool))),
            seed = 11,
        )

        val first = EnToZhBuilder().build(harbour, context.copy(attempts = mapOf(1L to 0)))!!
        val second = EnToZhBuilder().build(harbour, context.copy(attempts = mapOf(1L to 1)))!!

        assertEquals(4, first.options.size)
        assertEquals(4, second.options.size)
        assertNotEquals(
            "第二次要换掉至少一个干扰项",
            first.options.map { it.text }.toSet(),
            second.options.map { it.text }.toSet(),
        )
        assertTrue(first.options.all { it.gloss.isNotBlank() })
    }

    @Test
    fun noOptionIsTheAnswerTwiceUnderAnotherName() {
        val context = QuestionContext(
            pool = pool,
            enrichment = QuizEnrichment(
                mapOf(1L to EnrichedWord(distractors = listOf(EnrichedDistractor("dock", "港口"), EnrichedDistractor("pier", "t 码头")))),
            ),
            confusables = mapOf(1L to listOf(EnrichedDistractor("port", "港口"))),
            seed = 13,
        )

        val question = EnToZhBuilder().build(harbour, context)!!

        assertEquals(question.options.size, question.options.map { it.text }.distinct().size)
        assertFalse(question.options.filterNot { it.correct }.any { it.text == "港口" })
    }

    @Test
    fun collocationAsksWhatARealPhraseMeans() {
        val question = CollocationBuilder().build(harbour, collocationContext(seed = 21))!!

        assertEquals(QuizType.COLLOCATION, question.type)
        assertTrue("题干必须是这个词自己的真搭配", question.prompt in harbourCollocations.map { it.word })
        assertEquals(4, question.options.size)
        assertEquals(1, question.options.count { it.correct })
        assertEquals(
            "正确答案就是题干那条短语的意思",
            harbourCollocations.first { it.word == question.prompt }.zh,
            question.correctText(),
        )
        assertTrue("每个选项都要能揭开自己原本的短语", question.options.all { it.gloss.isNotBlank() })
    }

    @Test
    fun theWordNeverAppearsInTheOptions() {
        // 早先四个选项是英文短语、只有正确答案含这个词：照着找词就能选对。
        // 现在选项全是释义，答案只能靠读懂题干。
        val question = CollocationBuilder().build(harbour, collocationContext(seed = 22))!!

        assertFalse(
            "选项里不能再出现这个词，否则又变成找词游戏",
            question.options.any { it.text.contains("harbour", ignoreCase = true) },
        )
        assertEquals(question.options.size, question.options.map { it.text }.distinct().size)
        assertEquals(question.options.size, question.options.map { it.gloss }.distinct().size)
        assertEquals("题干那一条不会同时当某个选项的另一面", 1, question.options.count { it.gloss == question.prompt })
    }

    @Test
    fun aWordWithoutCollocationsGetsNoCollocationQuestion() {
        assertNull(
            CollocationBuilder().build(
                harbour,
                QuestionContext(pool = pool, collocationPool = otherCollocations),
            ),
        )
        assertNull(
            CollocationBuilder().build(
                harbour,
                QuestionContext(
                    enrichment = QuizEnrichment(mapOf(1L to EnrichedWord(definition = "a place where ships stay"))),
                    collocationPool = otherCollocations,
                ),
            ),
        )
    }

    @Test
    fun theNextAttemptAsksAnotherOfTheWordsOwnPhrasesAndAnotherDistractorSet() {
        val context = collocationContext(seed = 23)

        val first = CollocationBuilder().build(harbour, context.copy(attempts = mapOf(1L to 0)))!!
        val second = CollocationBuilder().build(harbour, context.copy(attempts = mapOf(1L to 1)))!!

        assertTrue(first.prompt in harbourCollocations.map { it.word })
        assertTrue(second.prompt in harbourCollocations.map { it.word })
        assertNotEquals("同一个词再考一次，别永远同一个短语", first.prompt, second.prompt)
        assertNotEquals(
            first.options.map { it.text }.toSet(),
            second.options.map { it.text }.toSet(),
        )
    }

    @Test
    fun aCollocationWithoutAGlossCannotBeTheAnswer() {
        val context = QuestionContext(
            enrichment = QuizEnrichment(
                mapOf(1L to EnrichedWord(collocations = listOf(EnrichedDistractor("harbour wall", "")))),
            ),
            collocationPool = otherCollocations,
        )

        assertNull("选项不能是一个空白", CollocationBuilder().build(harbour, context))
    }

    private fun collocationContext(seed: Long) = QuestionContext(
        enrichment = QuizEnrichment(mapOf(1L to EnrichedWord(collocations = harbourCollocations))),
        collocationPool = otherCollocations,
        seed = seed,
    )

    private val harbourCollocations = listOf(
        EnrichedDistractor("harbour wall", "防波堤"),
        EnrichedDistractor("harbour seal", "港海豹"),
    )

    /**
     * 干扰项从别的词的搭配里来。`take harbour` 在旧设计里是"含这个词所以不能当干扰项"的例子，
     * 现在反过来：它的意思与被考的短语无关，所以照样能当干扰项。
     */
    private val otherCollocations = listOf(
        EnrichedDistractor("take measures", "采取措施"),
        EnrichedDistractor("draw a conclusion", "下结论"),
        EnrichedDistractor("pay attention", "注意"),
        EnrichedDistractor("make progress", "取得进步"),
        EnrichedDistractor("take harbour", "占港"),
    )

    /** 摆在屏上的那一面是 [QuizOption.text]，答完才揭开的另一面是 [QuizOption.gloss]。 */
    private fun QuizQuestion.correctText(): String = options.first { it.correct }.text

    private fun QuizQuestion.correctGloss(): String = options.first { it.correct }.gloss

    /** 模型一次给一整池候选，出题时只抽其中三个，所以池子要比选项大。 */
    private val aiPool = listOf(
        EnrichedDistractor("dock", "码头"),
        EnrichedDistractor("pier", "桥墩"),
        EnrichedDistractor("quay", "泊位"),
        EnrichedDistractor("anchorage", "锚地"),
        EnrichedDistractor("estuary", "河口"),
        EnrichedDistractor("shoal", "浅滩"),
        EnrichedDistractor("cove", "海湾"),
    )

    private fun word(id: Long, text: String, zh: String, def: String = "") = WordEntity(
        id = id,
        word = text,
        translation = zh,
        definition = def,
        source = "test",
        createdAtSec = 0,
    )
}

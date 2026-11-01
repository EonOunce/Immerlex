package com.immerlex.core.material

import com.immerlex.core.db.QuizMaterialEntity
import com.immerlex.core.quiz.MaterialPool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MaterialMergeTest {

    private val stored = QuizMaterialEntity(
        word = "harbour",
        definition = "a place of shelter",
        example = "船进了 harbour。",
        exampleZh = "",
        distractors = """[{"w":"dock","zh":"码头"}]""",
        updatedAtSec = 100,
    )

    @Test
    fun aPackLineReplacesWhatItSaysAndKeepsWhatItDoesNot() {
        val merged = MaterialMerge.merge(
            stored,
            MaterialEntry(word = "harbour", definition = "a safe place for ships"),
            atSec = 200,
            glossOf = { "" },
        )

        assertEquals("a safe place for ships", merged.definition)
        assertEquals("包没提例句，AI 补的那一句就还在", "船进了 harbour。", merged.example)
        assertEquals("这一行没给例句，就不该有对照可挂", "", merged.exampleZh)
        assertEquals(listOf("dock"), MaterialPool.decode(merged.distractors).map { it.word })
        assertEquals(200L, merged.updatedAtSec)
    }

    @Test
    fun aNewExampleDropsTheGlossThatBelongsToTheOldOne() {
        val withGloss = stored.copy(example = "The boat returned to the harbour.", exampleZh = "船回到了港湾。")

        val merged = MaterialMerge.merge(
            withGloss,
            MaterialEntry(word = "harbour", example = "We anchored in the harbour."),
            atSec = 200,
            glossOf = { "" },
        )

        assertEquals("We anchored in the harbour.", merged.example)
        assertEquals("旧对照对不上新句子，留着只会误导", "", merged.exampleZh)
    }

    @Test
    fun candidatesAreGlossedFromTheDictionaryAndTheOnesNobodyKnowsAreDropped() {
        val entry = MaterialEntry(
            word = "adapt",
            definition = "to change",
            confusables = listOf(Confusable("adopt"), Confusable("adept", "熟练的"), Confusable("zzqx")),
        )

        val merged = MaterialMerge.merge(null, entry, atSec = 200) { if (it == "adopt") "采用" else "" }

        assertEquals(listOf("adopt", "adept"), MaterialPool.decode(merged.distractors).map { it.word })
        assertEquals(listOf("采用", "熟练的"), MaterialPool.decode(merged.distractors).map { it.zh })
    }

    @Test
    fun theSamePackTwiceWritesNothingTheSecondTime() {
        val entries = listOf(MaterialEntry(word = "harbour", definition = "a place of shelter"))
        val once = MaterialMerge.plan(entries, emptyMap(), atSec = 200) { "" }
        val twice = MaterialMerge.plan(entries, once.associateBy { it.word }, atSec = 300) { "" }

        assertEquals(1, once.size)
        assertEquals(entries.single().definition, once.single().definition)
        assertTrue("内容一个字没变就不算写进去过", twice.isEmpty())
    }

    @Test
    fun aFixedPackLineDoesRewriteTheRow() {
        val entries = listOf(MaterialEntry(word = "harbour", definition = "a place of shelter"))
        val existing = mapOf("harbour" to stored.copy(definition = "写错的那一句"))

        val changed = MaterialMerge.plan(entries, existing, atSec = 300) { "" }

        assertEquals("包修好了就该真的落库", listOf("harbour"), changed.map { it.word })
    }

    @Test
    fun aStrayGlossIsNotAttachedToTheOldExample() {
        // 包里这一行只有 exampleZh 却没有 example：那句 AI 写的中文句子对不上它，宁可当没给。
        val merged = MaterialMerge.merge(
            stored,
            MaterialEntry(word = "harbour", exampleZh = "船回到了港湾。"),
            atSec = 200,
            glossOf = { "" },
        )

        assertEquals("船进了 harbour。", merged.example)
        assertEquals("", merged.exampleZh)
    }

    @Test
    fun aPackSilentAboutCollocationsKeepsTheOnesAlreadyStored() {
        val withCollocations = stored.copy(collocations = """[{"w":"harbour wall","zh":"防波堤"}]""")

        val merged = MaterialMerge.merge(
            withCollocations,
            MaterialEntry(word = "harbour", definition = "a safe place"),
            atSec = 200,
            glossOf = { "" },
        )

        assertEquals(
            listOf("harbour wall"),
            MaterialPool.decode(merged.collocations).map { it.word },
        )
    }

    @Test
    fun aPackWithCollocationsReplacesTheWholePoolAndFillsGlossesFromTheDictionary() {
        val entry = MaterialEntry(
            word = "take",
            collocations = listOf(
                Collocation("take part in"),
                Collocation("take it easy", "放轻松"),
                Collocation("take turns", "  "),
            ),
        )

        val merged = MaterialMerge.merge(null, entry, atSec = 200) { if (it == "take part in") "参加" else "" }

        assertEquals(
            "两边的池子掺在一起就没人说得清是谁的",
            listOf("take part in", "take it easy"),
            MaterialPool.decode(merged.collocations).map { it.word },
        )
        assertEquals(listOf("参加", "放轻松"), MaterialPool.decode(merged.collocations).map { it.zh })
    }

    @Test
    fun onlyAFixedCollocationIsEnoughToRewriteTheRow() {
        val existing = mapOf(
            "take" to QuizMaterialEntity(word = "take", definition = "to hold", updatedAtSec = 100),
        )
        val entries = listOf(
            MaterialEntry(word = "take", collocations = listOf(Collocation("take part in", "参加"))),
        )

        val changed = MaterialMerge.plan(entries, existing, atSec = 300) { "" }

        assertEquals(listOf("take"), changed.map { it.word })
        assertEquals(listOf("take part in"), MaterialPool.decode(changed.single().collocations).map { it.word })
    }
}

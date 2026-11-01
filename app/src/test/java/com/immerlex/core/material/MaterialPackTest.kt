package com.immerlex.core.material

import com.immerlex.core.db.QuizMaterialEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MaterialPackTest {

    @Test
    fun aWellFormedPackIsReadWhole() {
        val parsed = MaterialPack.parse(
            """
            [
              {"word":"harbour","definition":"a place of shelter","example":"The boat returned to the harbour.",
               "exampleZh":"船回到了港湾。","distractors":[{"word":"harvest","zh":"收获"}]},
              {"word":"  In Case Of ","definition":"if something happens","example":"In case of fire, leave now.","distractors":["during","losing"]}
            ]
            """.trimIndent(),
        )

        assertNull(parsed.failure)
        val entries = parsed.read.entries
        assertEquals(2, entries.size)
        assertEquals("harbour", entries[0].word)
        assertEquals("船回到了港湾。", entries[0].exampleZh)
        assertEquals(listOf(Confusable("harvest", "收获")), entries[0].confusables)
        assertEquals("in case of", entries[1].word)
        assertEquals(listOf(Confusable("during"), Confusable("losing")), entries[1].confusables)
    }

    @Test
    fun oneBadLineOnlyCostsThatLine() {
        val parsed = MaterialPack.parse(
            """
            [
              {"word":"adapt","definition":"to change"},
              {"definition":"没有词面"},
              {"word":"adapt","definition":"重复的一行"},
              {"word":"ubiquitous","definition":"   ","example":""},
              {"word":"12345","definition":"符号不是词"}
            ]
            """.trimIndent(),
        )

        assertEquals(listOf("adapt"), parsed.read.entries.map { it.word })
        assertEquals(
            mapOf(DropReason.NO_WORD to 2, DropReason.DUPLICATE to 1, DropReason.NO_CONTENT to 1),
            parsed.read.dropped,
        )
    }

    @Test
    fun aPackWrappedInAnObjectIsStillRead() {
        val parsed = MaterialPack.parse("""{"words":[{"word":"adapt","definition":"to change"}]}""")

        assertNull(parsed.failure)
        assertEquals("adapt", parsed.read.entries.single().word)
    }

    @Test
    fun aFileThatIsNotAPackSaysSo() {
        assertEquals(PackFailure.NOT_JSON, MaterialPack.parse("这不是 JSON").failure)
        assertEquals(PackFailure.NOT_ARRAY, MaterialPack.parse("""{"meaning":"不是清单"}""").failure)
        assertEquals(PackFailure.EMPTY, MaterialPack.parse("[]").failure)
    }

    @Test
    fun aLongFieldIsClippedInsteadOfRefused() {
        val parsed = MaterialPack.parse(
            """[{"word":"adapt","definition":"${"释".repeat(400)}","example":"${"句".repeat(400)}"}]""",
        )

        assertEquals(MaterialPack.MAX_DEFINITION, parsed.read.entries.single().definition.length)
        assertEquals(MaterialPack.MAX_TEXT, parsed.read.entries.single().example.length)
    }

    @Test
    fun unknownKeysAndExtraGlossesAreIgnored() {
        val parsed = MaterialPack.parse(
            """[{"word":"adapt","translation":"适应","definition":"to change","tags":["考研"],
               "distractors":[{"w":"adopt","translation":"采用"},{"word":"adept","gloss":"熟练的"}]}]""",
        )

        val entry = parsed.read.entries.single()
        assertEquals("to change", entry.definition)
        // translation 是词义，不是例句的中文对照——没有例句的行不能凭它造一句"翻译"。
        assertEquals("", entry.exampleZh)
        assertEquals(listOf(Confusable("adopt", "采用"), Confusable("adept", "熟练的")), entry.confusables)
    }

    @Test
    fun tooManyCandidatesKeepTheFirstOnes() {
        val pool = ('a'..'t').joinToString(",") { """{"word":"dock$it","zh":"码头$it"}""" }

        val entry = MaterialPack.parse("""[{"word":"dock","definition":"a place","distractors":[$pool]}]""")
            .read.entries.single()

        assertEquals(MaterialPack.MAX_CONFUSABLES, entry.confusables.size)
        assertEquals("docka", entry.confusables.first().word)
    }

    @Test
    fun aCollocationMustReallyContainTheWordItIsFiledUnder() {
        val read = MaterialPack.parse(
            """
            [{"word":"take","collocations":[
              {"en":"take measures","zh":"采取措施"},
              {"en":"make progress","zh":"取得进步"},
              "take part in",
              {"phrase":"take it easy","gloss":"放轻松"},
              {"en":"take","zh":"拿"},
              {"en":"Take Measures","zh":"重复的一条"}
            ]}]
            """.trimIndent(),
        ).read

        assertEquals(
            "别的词的搭配、比词还短的串、重复的一条都不该留下",
            listOf(
                Collocation("take measures", "采取措施"),
                Collocation("take part in", ""),
                Collocation("take it easy", "放轻松"),
            ),
            read.entries.single().collocations,
        )
        assertEquals(
            "不含这个词的两条要单独报数，用户才能拿回去让生成方改；纯粹写重了的那条不算错",
            2,
            read.badCollocations,
        )
        assertTrue("只有搭配也算这一行有内容", read.entries.single().hasContent)
    }

    @Test
    fun aWordWhoseCollocationsAreAllWrongIsStillAnEntry() {
        val read = MaterialPack.parse(
            """[{"word":"take","definition":"to get hold of","collocations":["make progress","pay attention"]}]""",
        ).read

        assertEquals("take", read.entries.single().word)
        assertEquals("to get hold of", read.entries.single().definition)
        assertEquals("没有一条搭配能用，但这条词本身不该跟着被丢", 2, read.badCollocations)
    }

    @Test
    fun aCleanPackReportsNoBadCollocations() {
        val read = MaterialPack.parse(
            """[{"word":"take","collocations":[{"en":"take measures","zh":"采取措施"}]},{"word":"draw","definition":"to pull"}]""",
        ).read

        assertEquals(0, read.badCollocations)
        assertEquals(2, read.entries.size)
    }

    @Test
    fun tooManyCollocationsKeepTheFirstOnesAndLongGlossesAreClipped() {
        val pool = (1..6).joinToString(",") { """{"en":"take $it","zh":"${"释".repeat(80)}"}""" }

        val entry = MaterialPack.parse("""[{"word":"take","collocations":[$pool]}]""")
            .read.entries.single()

        assertEquals(MaterialPack.MAX_COLLOCATIONS, entry.collocations.size)
        assertEquals("take 1", entry.collocations.first().en)
        assertEquals(MaterialPack.MAX_GLOSS, entry.collocations.first().zh.length)
    }

    @Test
    fun collocationsSurviveTheRoundTripThroughTheDatabaseRow() {
        val row = QuizMaterialEntity(
            word = "take",
            collocations = """[{"w":"take measures","zh":"采取措施"},{"en":"take part in"},{"phrase":"take off"}]""",
            updatedAtSec = 1,
        )

        val entry = MaterialPack.parse(MaterialPack.render(listOf(row))).read.entries.single()

        assertEquals(
            listOf(
                Collocation("take measures", "采取措施"),
                Collocation("take part in", ""),
                Collocation("take off", ""),
            ),
            entry.collocations,
        )
    }

    @Test
    fun whatTheAppWritesOutReadsBackTheSame() {
        val rows = listOf(
            QuizMaterialEntity(
                word = "harbour",
                definition = "a place of shelter",
                example = "The boat returned to the harbour.",
                exampleZh = "船回到了港湾。",
                distractors = """[{"w":"harvest","zh":"收获"}]""",
                updatedAtSec = 1700000000,
            ),
            // AI 自己写的那一列可能是空数组，也可能压根是坏 JSON：导出不能让整包跟着坏掉。
            QuizMaterialEntity(word = "adapt", definition = "to change", distractors = "{oops", updatedAtSec = 1),
            QuizMaterialEntity(word = "ubiquitous", example = "Phones are ubiquitous.", distractors = "[]", updatedAtSec = 2),
        )

        val read = MaterialPack.parse(MaterialPack.render(rows)).read

        assertEquals(3, read.entries.size)
        assertEquals("harbour", read.entries[0].word)
        assertEquals("船回到了港湾。", read.entries[0].exampleZh)
        assertEquals(listOf(Confusable("harvest", "收获")), read.entries[0].confusables)
        assertEquals(listOf("adapt", "ubiquitous"), read.entries.drop(1).map { it.word })
        assertTrue(read.entries[1].example.isEmpty())
        assertEquals("Phones are ubiquitous.", read.entries[2].example)
    }
}

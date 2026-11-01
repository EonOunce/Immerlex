package com.immerlex.core.material

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.immerlex.core.db.ImmerlexDatabase
import com.immerlex.core.dict.DictEntry
import com.immerlex.core.dict.DictionarySource
import com.immerlex.core.quiz.MaterialPool
import com.immerlex.core.quiz.QuizMaterialStore
import com.immerlex.core.db.WordEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 素材包在真库上进出：一次几千行的包要切着问、切着写，而且词库里没有这个词也得收得下。 */
@RunWith(AndroidJUnit4::class)
class MaterialImportTest {

    private lateinit var db: ImmerlexDatabase
    private lateinit var repository: MaterialRepository

    @Before
    fun openDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, ImmerlexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = MaterialRepository(db, StubDictionary)
    }

    @After
    fun closeDatabase() {
        db.close()
    }

    @Test
    fun aPackBiggerThanOneQueryCanHoldIsWrittenWhole() = runBlocking {
        val words = (0..1_199).map { surfaceOf(it) }

        val outcome = repository.import(packOf(words))

        assertTrue(outcome is ImportOutcome.Imported)
        assertEquals(words.size, (outcome as ImportOutcome.Imported).written)
        assertEquals(words.size, repository.count())
        assertEquals(0, db.wordDao().allWords().size)
    }

    @Test
    fun importingTheSamePackAgainWritesNothingButKeepsEveryRow() = runBlocking {
        val words = (0..600).map { surfaceOf(it) }
        repository.import(packOf(words))

        val again = repository.import(packOf(words))

        assertEquals("内容一个字没变，就不该报第二次写入", 0, (again as ImportOutcome.Imported).written)
        assertEquals(words.size, repository.count())
    }

    @Test
    fun aWordThatOnlyThePackKnowsStillAnswersWhenTheBankAsks() = runBlocking {
        repository.import("""[{"word":"harbour","definition":"a place of shelter","distractors":["adopt","zzqx"]}]""")

        // 词库里的 harbour 是新加的一条，id 与包无关。
        val stored = QuizMaterialStore(db.quizMaterialDao())
            .load(listOf(WordEntity(id = 77, word = "Harbour", translation = "港湾", source = "test", createdAtSec = 0)))

        assertEquals("a place of shelter", stored[77L]?.definition)
        val row = db.quizMaterialDao().byWords(listOf("harbour")).single()
        // 词典查不到释义的候选丢掉：选项不能是空白。
        assertEquals(listOf("adopt"), MaterialPool.decode(row.distractors).map { it.word })
    }

    @Test
    fun aFileThatIsNotAPackLeavesTheTableAlone() = runBlocking {
        repository.import("""[{"word":"adapt","definition":"to change"}]""")

        val outcome = repository.import("今晚八点老地方见，记得带上笔记")

        // 读不懂就直说读不懂：既不能当成"这个包是空的"，更不能动已经收下的那些行。
        assertTrue(outcome is ImportOutcome.Rejected)
        assertEquals(1, repository.count())
    }

    @Test
    fun peekGivesTheDictionaryCardWhatTheWordAlreadyHas() = runBlocking {
        val outcome = repository.import(
            """[{"word":"harbour","definition":"a place of shelter","example":"The harbour was quiet.","exampleZh":"港口很安静。","distractors":[{"word":"port","zh":"港口"}]}]""",
        )

        assertTrue("包应该整行收下：$outcome", outcome is ImportOutcome.Imported)
        val peeked = repository.peek("  Harbour ")

        assertEquals("a place of shelter", peeked?.definition)
        assertEquals("The harbour was quiet.", peeked?.example)
        assertEquals("港口很安静。", peeked?.exampleZh)
        assertEquals(listOf("port" to "港口"), peeked?.confusables?.map { it.word to it.zh })
        assertNull("没有那一行就是空的", repository.peek("ubiquitous"))
    }

    @Test
    fun whatWasImportedExportsAndReadsBackIdentically() = runBlocking {
        val words = (0..450).map { surfaceOf(it) }
        repository.import(packOf(words))

        val reread = MaterialPack.parse(repository.export()).read

        assertTrue(reread.dropped.isEmpty())
        assertEquals(words.size, reread.entries.size)
        assertEquals(words.sorted(), reread.entries.map { it.word })
    }

    private fun packOf(words: List<String>) =
        words.joinToString(",", "[", "]") { """{"word":"$it","definition":"meaning of $it"}""" }

    /** 词面只认字母，所以测试里的编号也折成字母：wabc 这样三位，够用一万六千个。 */
    private fun surfaceOf(index: Int): String {
        val a = 'a' + index / 676 % 26
        val b = 'a' + index / 26 % 26
        val c = 'a' + index % 26
        return "w$a$b$c"
    }

    private object StubDictionary : DictionarySource {
        override fun lookup(word: String): DictEntry? =
            if (word == "adopt") DictEntry(word, "", "采用", "") else null

        override fun search(needle: String, limit: Int): List<DictEntry> =
            listOfNotNull(lookup(needle.trim().lowercase())).take(limit)
    }
}

package com.immerlex.core.tricky

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.immerlex.core.db.ImmerlexDatabase
import com.immerlex.core.db.WordEntity
import com.immerlex.core.dict.DictionarySource
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 管理页读写的其实是一张关系图：批量新增要两两挂上，重叠的两行会并成一组，
 * 移出一个词只断它的连线、不丢易错标记。这些跨过 Room 事务与唯一索引，纯 JVM 单测碰不到。
 */
@RunWith(AndroidJUnit4::class)
class TrickyGroupStoreTest {

    private lateinit var db: ImmerlexDatabase
    private lateinit var repo: TrickyRepository

    private object NoDictionary : DictionarySource {
        override fun lookup(word: String) = null
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            ImmerlexDatabase::class.java,
        ).allowMainThreadQueries().build()
        repo = TrickyRepository(db, db.wordDao(), NoDictionary)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun twoLinesThatShareAWordBecomeOneGroup() = runBlocking {
        assertEquals(2, repo.addGroups("affect, effect\nadapt, adopt"))
        assertEquals(listOf(listOf("adapt", "adopt"), listOf("affect", "effect")), wordsOf(repo.groups()))

        assertEquals(1, repo.addGroups("adopt, adept"))
        assertEquals(
            listOf(listOf("adapt", "adept", "adopt"), listOf("affect", "effect")),
            wordsOf(repo.groups()),
        )
    }

    @Test
    fun glossComesFromTheWordBankAndUnknownWordsStillJoinTheGroup() = runBlocking {
        db.wordDao().insert(
            WordEntity(
                word = "affect",
                phonetic = "/ə'fekt/",
                translation = "影响",
                source = "test",
                createdAtSec = Instant.now().epochSecond,
            ),
        )
        repo.addGroups("affect, never-seen-this")

        val group = repo.groups().single()
        assertEquals(listOf("affect", "never-seen-this"), group.words)
        assertEquals("影响", group.members.first { it.word == "affect" }.gloss)
        assertEquals("", group.members.first { it.word == "never-seen-this" }.gloss)
    }

    @Test
    fun removingAWordFromItsGroupKeepsItMarkedAndKeepsTheOthersTogether() = runBlocking {
        repo.addGroups("a, b, c")
        val group = repo.groups().single()

        repo.removeFromGroup(group, "b")

        assertEquals(listOf(listOf("a", "c"), listOf("b")), wordsOf(repo.groups()))
        assertEquals(listOf("a" to "c"), db.trickyDao().allLinks().map { it.wordA to it.wordB })
        assertEquals("移出后这个词还留着易错标记", 1, db.trickyDao().all().count { it.word == "b" })
    }

    @Test
    fun addingAWordIntoAGroupLinksItToEveryMember() = runBlocking {
        repo.addGroups("a, b")
        assertTrue(repo.addToGroup(repo.groups().single(), " C "))

        val group = repo.groups().single()
        assertEquals(listOf("a", "b", "c"), group.words)
        assertEquals(3, db.trickyDao().allLinks().size)
        assertEquals(false, repo.addToGroup(group, "a"))
    }

    @Test
    fun deletingASingletonTakesItsMarkAwayToo() = runBlocking {
        repo.addGroups("a, b")
        repo.toggle("c", "", "")

        repo.deleteWord(repo.groups().single { it.members.size == 1 }.key)

        assertEquals(listOf(listOf("a", "b")), wordsOf(repo.groups()))
        assertTrue(db.trickyDao().all().none { it.word == "c" })
    }

    private fun wordsOf(groups: List<TrickyGroup>): List<List<String>> = groups.map { it.words }
}

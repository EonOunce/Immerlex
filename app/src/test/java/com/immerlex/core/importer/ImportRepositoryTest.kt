package com.immerlex.core.importer

import com.immerlex.core.db.WordEntity
import com.immerlex.testsupport.FakeDictionary
import com.immerlex.testsupport.FakeWordDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImportRepositoryTest {

    private val dictionary = FakeDictionary(
        mapOf(FakeDictionary.entry("apple", translation = "n. 苹果", phonetic = "/ˈæpl/", definition = "a fruit")),
    )

    @Test
    fun dictionaryEntryWinsOverImportedGloss() = runBlocking {
        val dao = FakeWordDao()
        val summary = ImportRepository(dao, dictionary)
            .importWords(listOf(ParsedWord("apple", "用户自己写的释义")), source = "txt")

        assertEquals(1, summary.added)
        assertEquals(1, summary.dictMatched)
        assertEquals(0, summary.missingGloss)
        val saved = checkNotNull(dao.findByWord("apple"))
        assertEquals("n. 苹果", saved.translation)
        assertEquals("/ˈæpl/", saved.phonetic)
        assertEquals("a fruit", saved.definition)
    }

    @Test
    fun importedGlossIsKeptWhenDictionaryMisses() = runBlocking {
        val dao = FakeWordDao()
        val summary = ImportRepository(dao, dictionary)
            .importWords(listOf(ParsedWord("in spite of", "尽管")), source = "ocr")

        assertEquals(1, summary.added)
        assertEquals(0, summary.dictMatched)
        assertEquals("尽管", checkNotNull(dao.findByWord("in spite of")).translation)
    }

    @Test
    fun wordWithoutAnyGlossIsCountedAsMissing() = runBlocking {
        val dao = FakeWordDao()
        val summary = ImportRepository(dao, dictionary).importWords(listOf(ParsedWord("zzyzx", null)), source = "txt")

        assertEquals(1, summary.added)
        assertEquals(1, summary.missingGloss)
        assertEquals("", checkNotNull(dao.findByWord("zzyzx")).translation)
    }

    @Test
    fun repeatsAreCountedAsDuplicates() = runBlocking {
        val dao = FakeWordDao()
        val repo = ImportRepository(dao, dictionary)
        repo.importWords(listOf(ParsedWord("apple", null)), source = "txt")
        val second = repo.importWords(listOf(ParsedWord("apple", null)), source = "txt")

        assertEquals(0, second.added)
        assertEquals(1, second.duplicates)
        assertEquals(1, dao.totalCount())
    }

    @Test
    fun prepareFlagsANewWordWhoseGlossIsIdenticalToAnExistingOne() = runBlocking {
        val dao = FakeWordDao()
        dao.insert(bankWord("harbor", "n. 港, 避难所"))
        val repo = ImportRepository(dao, variantsDictionary)

        val plan = repo.prepare(listOf(ParsedWord("harbour", null)))

        assertEquals(listOf(SameGlossPair("harbour", "harbor", "n. 港, 避难所")), plan.variants)
        assertEquals(1, plan.words.size)
    }

    @Test
    fun twoNewWordsAreComparedWithEachOtherToo() = runBlocking {
        val dao = FakeWordDao()
        val plan = ImportRepository(dao, variantsDictionary)
            .prepare(listOf(ParsedWord("harbor", null), ParsedWord("harbour", null)))

        assertEquals(listOf(SameGlossPair("harbour", "harbor", "n. 港, 避难所")), plan.variants)
    }

    @Test
    fun skippingVariantsLeavesThemOutOfTheBank() = runBlocking {
        val dao = FakeWordDao()
        dao.insert(bankWord("harbor", "n. 港, 避难所"))
        val repo = ImportRepository(dao, variantsDictionary)
        val plan = repo.prepare(listOf(ParsedWord("harbour", null), ParsedWord("apple", null)))

        val summary = repo.commit(plan, source = "txt", skipVariants = true)

        assertEquals(1, summary.added)
        assertEquals(1, summary.skippedVariants)
        assertNull(dao.findByWord("harbour"))
        assertEquals("n. 苹果", checkNotNull(dao.findByWord("apple")).translation)
    }

    @Test
    fun keepingBothWritesBothRows() = runBlocking {
        val dao = FakeWordDao()
        dao.insert(bankWord("harbor", "n. 港, 避难所"))
        val repo = ImportRepository(dao, variantsDictionary)
        val plan = repo.prepare(listOf(ParsedWord("harbour", null)))

        val summary = repo.commit(plan, source = "txt", skipVariants = false)

        assertEquals(1, summary.added)
        assertEquals(0, summary.skippedVariants)
        assertEquals(2, dao.totalCount())
    }

    private val variantsDictionary = FakeDictionary(
        mapOf(
            FakeDictionary.entry("harbor", translation = "n. 港, 避难所"),
            FakeDictionary.entry("harbour", translation = "n. 港, 避难所"),
            FakeDictionary.entry("apple", translation = "n. 苹果"),
        ),
    )

    private fun bankWord(word: String, translation: String) = WordEntity(
        word = word,
        translation = translation,
        source = "txt",
        createdAtSec = 0,
    )
}

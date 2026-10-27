package com.immerlex.core.importer

import com.immerlex.testsupport.FakeDictionary
import com.immerlex.testsupport.FakeWordDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
}

package com.immerlex.core.dict

data class DictEntry(
    val word: String,
    val phonetic: String,
    val translation: String,
    val definition: String,
)

interface DictionarySource {
    fun lookup(word: String): DictEntry?

    /**
     * 按用户敲进去的几个字查词典，只回前 [limit] 条。
     * 结果不进词库、不排复习——它回答的是"词典里有没有这个词"，不是"这个词该什么时候背"。
     */
    fun search(needle: String, limit: Int): List<DictEntry>
}

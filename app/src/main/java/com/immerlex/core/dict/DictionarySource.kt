package com.immerlex.core.dict

data class DictEntry(
    val word: String,
    val phonetic: String,
    val translation: String,
    val definition: String,
)

interface DictionarySource {
    fun lookup(word: String): DictEntry?
}

package com.immerlex.core.dict

/** 一次词典检索的形状：查哪一列、用哪个模式。SQL 留在 `EcdictDictionary`，这里的规矩能单测。 */
enum class SearchColumn { WORD, TRANSLATION }

data class DictionaryQuery(val column: SearchColumn, val pattern: String)

/**
 * 用户敲进去的那几个字，怎么变成一条查词典的语句。
 *
 * 英文按**开头**匹配（`dict.word` 是小写且有主键索引，前缀查得起），中文按**包含**匹配
 * （释义写在 translation 里，没有索引可言）。`%` 和 `_` 是通配符，用户打这两个字符时
 * 要当字面量，否则一个 `%` 就能把整本词典捞出来。
 */
object DictionarySearch {
    const val MIN_LENGTH = 2

    fun of(raw: String): DictionaryQuery? {
        val needle = raw.trim()
        if (needle.length < MIN_LENGTH) return null
        return if (needle.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it in charArrayOf('-', ' ', '\'') }) {
            DictionaryQuery(SearchColumn.WORD, escape(needle.lowercase()) + "%")
        } else {
            DictionaryQuery(SearchColumn.TRANSLATION, "%" + escape(needle) + "%")
        }
    }

    private fun escape(needle: String): String =
        needle.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}

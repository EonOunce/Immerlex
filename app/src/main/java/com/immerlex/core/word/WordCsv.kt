package com.immerlex.core.word

import com.immerlex.core.db.WordEntity

/**
 * 词库导出为两列 CSV（英文, 中文释义），UTF-8、无表头，Anki 的“从文件导入”可直接读。
 * 释义里的逗号、引号、换行按 RFC 4180 转义——少转一个逗号，后面每一列都会错位。
 */
fun wordCsvOf(words: List<WordEntity>): String = buildString {
    words.forEach { word ->
        append(csvField(word.word))
        append(',')
        append(csvField(word.translation))
        append('\n')
    }
}

internal fun csvField(value: String): String =
    if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"" + value.replace("\"", "\"\"") + "\""
    } else {
        value
    }

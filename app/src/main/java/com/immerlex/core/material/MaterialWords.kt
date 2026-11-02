package com.immerlex.core.material

import com.immerlex.core.word.wordSurface

/**
 * 词面清单：只有词、一行一个、按字母序，拿去让别处的模型避开已经备过素材的词。
 * 词面归一化仍走 [wordSurface]，别在这里另写一份 trim + lowercase。
 */
fun wordListOf(words: List<String>): String =
    words.map(::wordSurface)
        .filter { it.isNotEmpty() }
        .distinct()
        .sorted()
        .joinToString("\n")

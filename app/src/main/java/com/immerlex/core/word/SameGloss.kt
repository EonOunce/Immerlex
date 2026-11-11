package com.immerlex.core.word

/**
 * 释义逐字相同的两个词面算"同一个意思"。
 *
 * ECDICT 里 harbor / harbour、who / whom 这类拼写变体或近义词的 translation 一字不差，
 * 它们要是同时进词库，就会在测验里互相当对方的中文选项，一道题有两个正确答案。
 * 判据只认整串相等，不猜拼写距离：「four」和「for」只差一个字母，释义却完全不同，
 * 按字母规则判会把正常的一对判成变体。见 docs/adr/0031-same-gloss-variants.md。
 */
object SameGloss {

    fun same(a: String?, b: String?): Boolean {
        val left = a?.trim().orEmpty()
        return left.isNotEmpty() && left == b?.trim().orEmpty()
    }
}

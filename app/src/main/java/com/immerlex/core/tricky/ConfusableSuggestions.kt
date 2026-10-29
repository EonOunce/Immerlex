package com.immerlex.core.tricky

import kotlin.math.abs

/**
 * 易混候选的排序：先给常见混淆对，再按拼写距离补上词库里长得像的词。
 * 只负责推荐，是否真的关联由用户在词卡上点选，绝不自动建立关系。
 */
object ConfusableSuggestions {

    fun suggestionsOf(word: String, candidates: Collection<String>, limit: Int = 4): List<String> {
        val key = word.trim().lowercase()
        if (key.isEmpty()) return emptyList()
        val curated = CONFUSABLE_PAIRS.flatMap { (a, b) ->
            when (key) {
                a -> listOf(b)
                b -> listOf(a)
                else -> emptyList()
            }
        }
        val threshold = if (key.length <= 4) 1 else 2
        // 三字母词只收等长邻居：cat 的插入/删减邻居（coat、at）几乎全是噪声。
        val maxLengthDiff = if (key.length <= 3) 0 else threshold
        val spelling = candidates.asSequence()
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() && it != key && WORDS_ONLY.matches(it) }
            .map { it to distance(key, it) }
            .filter { (other, d) ->
                d <= threshold && d < other.length && abs(other.length - key.length) <= maxLengthDiff
            }
            .sortedWith(compareBy({ it.second }, { it.first }))
            .map { it.first }
            .toList()
        return (curated + spelling).distinct().take(limit)
    }

    private fun distance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val swap = prev
            prev = cur
            cur = swap
        }
        return prev[b.length]
    }

    private val WORDS_ONLY = Regex("[a-z][a-z'-]*")

    private val CONFUSABLE_PAIRS = listOf(
        "affect" to "effect",
        "adapt" to "adopt",
        "advice" to "advise",
        "aloud" to "allowed",
        "bare" to "bear",
        "brake" to "break",
        "compliment" to "complement",
        "desert" to "dessert",
        "ensure" to "insure",
        "assure" to "ensure",
        "farther" to "further",
        "heal" to "heel",
        "later" to "latter",
        "loose" to "lose",
        "quiet" to "quite",
        "than" to "then",
        "their" to "there",
        "weather" to "whether",
        "accept" to "except",
        "access" to "excess",
        "principal" to "principle",
        "stationary" to "stationery",
        "conscience" to "conscious",
        "through" to "thorough",
        "though" to "thought",
        "beside" to "besides",
        "rise" to "raise",
        "sit" to "seat",
        "lie" to "lay",
        "borrow" to "lend",
        "bring" to "take",
        "rob" to "steal",
        "remind" to "remember",
        "spend" to "cost",
        "win" to "earn",
        "lend" to "loan",
        "sensible" to "sensitive",
        "economic" to "economical",
        "historic" to "historical",
        "classic" to "classical",
        "continuous" to "continual",
        "alternate" to "alternative",
        "imaginary" to "imaginative",
        "respectful" to "respectable",
        "respective" to "respectful",
        "childish" to "childlike",
        "industrious" to "industrial",
        "comprehensible" to "comprehensive",
        "intelligent" to "intelligible",
        "literacy" to "literature",
        "physician" to "physicist",
        "vocation" to "vacation",
        "dairy" to "diary",
        "angel" to "angle",
        "altar" to "alter",
        "council" to "counsel",
        "fair" to "fare",
        "floor" to "flour",
        "hole" to "whole",
        "knight" to "night",
        "plain" to "plane",
        "right" to "write",
        "sea" to "see",
        "son" to "sun",
        "steal" to "steel",
        "tail" to "tale",
        "weak" to "week",
        "wait" to "weight",
        "site" to "sight",
        "cite" to "site",
        "flair" to "flare",
        "faint" to "feint",
        "draft" to "draught",
    )
}

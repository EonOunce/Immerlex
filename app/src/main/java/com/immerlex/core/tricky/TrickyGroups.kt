package com.immerlex.core.tricky

/**
 * 易混词分组的纯规则：批量文本怎么切成组，关系图怎么折成组。
 * 组就是关系图上的连通分量，所以落单的词自成一组，a-b、b-c 会把三个词并成一组。
 */
object TrickyGroups {

    /** 一行一组；组内用半角或全角逗号分隔，同一行里重复的词只算一次，凑不出两个词的行丢掉。 */
    fun parse(text: String): List<List<String>> = text.lineSequence()
        .map { line -> line.split(',', '，').map(::normalize).distinct().filter(String::isNotEmpty) }
        .filter { it.size >= MIN_GROUP_SIZE }
        .toList()

    /** [links] 里两边的词都要在 [words] 里才参与合并，否则删掉一个词会把不相干的组黏在一起。 */
    fun components(words: List<String>, links: List<Pair<String, String>>): List<List<String>> {
        val alive = words.map(::normalize).filter(String::isNotEmpty).distinct()
        if (alive.isEmpty()) return emptyList()
        val rank = alive.withIndex().associate { (at, word) -> word to at }
        val parent = MutableList(alive.size) { it }
        fun root(of: Int): Int {
            var node = of
            while (parent[node] != node) {
                parent[node] = parent[parent[node]]
                node = parent[node]
            }
            return node
        }
        links.forEach { (left, right) ->
            val a = rank[normalize(left)] ?: return@forEach
            val b = rank[normalize(right)] ?: return@forEach
            val (x, y) = root(a) to root(b)
            if (x != y) parent[y] = x
        }
        return alive.groupBy { root(rank.getValue(it)) }
            .map { (_, members) -> members.sorted() }
            .sortedBy { it.first() }
    }

    /** 一组词互相挂上的全部有序对：批量新增与往组里加词都按这个补关系。 */
    fun pairsOf(words: List<String>): List<Pair<String, String>> {
        val members = words.map(::normalize).filter(String::isNotEmpty).distinct().sorted()
        return members.flatMapIndexed { at, left ->
            members.drop(at + 1).map { right -> pairOf(left, right) }
        }
    }

    fun normalize(word: String): String = word.trim().lowercase()

    fun pairOf(first: String, second: String): Pair<String, String> =
        if (first <= second) first to second else second to first

    private const val MIN_GROUP_SIZE = 2
}

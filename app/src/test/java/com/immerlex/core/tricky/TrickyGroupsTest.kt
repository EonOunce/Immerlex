package com.immerlex.core.tricky

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 分组的两条纯规则：批量文本怎么切成组，关系怎么折成组。
 * 组必须完全由关系算出来，界面上自己拼就会把 a-b、b-c 这种链看成两组。
 */
class TrickyGroupsTest {

    @Test
    fun oneLineIsOneGroupAndBothCommasSplitIt() {
        assertEquals(
            listOf(listOf("affect", "effect"), listOf("adapt", "adopt")),
            TrickyGroups.parse("affect, effect\nadapt，adopt"),
        )
    }

    @Test
    fun parseTrimsLowercasesAndDropsLinesThatCannotFormAGroup() {
        assertEquals(
            listOf(listOf("adapt", "adopt"), listOf("affect", "effect")),
            TrickyGroups.parse("  Adapt ,  ADOPT \n\n lone \n 一个词不成组 \n affect, effect \n"),
        )
    }

    @Test
    fun repeatedWordsInsideOneLineCountOnce() {
        assertEquals(listOf(listOf("affect", "effect")), TrickyGroups.parse("affect, effect, AFFECT,"))
    }

    @Test
    fun aChainOfRelationsIsStillOneGroupAndLonersKeepTheirOwn() {
        val groups = TrickyGroups.components(
            words = listOf("adapt", "affect", "adopt", "effect", "lone"),
            links = listOf("affect" to "effect", "adopt" to "adapt", "effect" to "adapt"),
        )
        assertEquals(listOf(listOf("adapt", "adopt", "affect", "effect"), listOf("lone")), groups)
    }

    @Test
    fun aLinkToADeletedWordDoesNotGlueTwoUnrelatedGroups() {
        val groups = TrickyGroups.components(
            words = listOf("a", "b", "d"),
            links = listOf("a" to "b", "b" to "c", "c" to "d"),
        )
        assertEquals(listOf(listOf("a", "b"), listOf("d")), groups)
    }

    @Test
    fun groupOrderIsStableSoTheListDoesNotShuffleAfterEveryEdit() {
        assertEquals(
            listOf(listOf("a", "z"), listOf("m"), listOf("q", "t")),
            TrickyGroups.components(
                words = listOf("q", "m", "z", "a", "t"),
                links = listOf("q" to "t", "a" to "z"),
            ),
        )
    }

    @Test
    fun everyPairOfAGroupIsStoredOnceInCanonicalOrder() {
        assertEquals(
            listOf("a" to "b", "a" to "c", "b" to "c"),
            TrickyGroups.pairsOf(listOf("C", "a", "b", "a")),
        )
    }
}

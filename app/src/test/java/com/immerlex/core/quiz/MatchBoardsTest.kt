package com.immerlex.core.quiz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchBoardsTest {

    private fun pairs(count: Int): List<MatchPair> =
        (1..count).map { MatchPair(it.toLong(), "word$it", "释义$it") }

    @Test
    fun blankAndDuplicatePairsNeverTakeAStand() {
        val pairs = listOf(
            MatchPair(1, "harbour", "港口"),
            // 释义逐字相同：两张中文牌一模一样，连上看不出对错，所以只留先来的那个。
            MatchPair(2, "harbor", "港口"),
            MatchPair(3, "lantern", "  "),
            MatchPair(4, "  ", "灯笼"),
            MatchPair(5, "Harbour", "港湾"),
            MatchPair(6, "kiln", "窑"),
        )

        val usable = MatchBoards.usable(pairs)

        assertEquals(listOf(1L, 6L), usable.map { it.wordId })
    }

    @Test
    fun boardsSplitEvenlyAndNeverHoldMoreThanFivePairs() {
        val boards = MatchBoards.boards(pairs(21), seed = 7)

        assertEquals("21 对分 5 屏", 5, boards.size)
        assertEquals(listOf(5, 4, 4, 4, 4), boards.map { it.left.size })
        assertEquals(21, boards.sumOf { it.left.size })
    }

    @Test
    fun bothColumnsHoldExactlyTheSameWords() {
        val boards = MatchBoards.boards(pairs(12), seed = 3)

        boards.forEach { board ->
            assertEquals(board.left.map { it.wordId }.toSet(), board.right.map { it.wordId }.toSet())
        }
        assertEquals(
            "左右两列各自打乱，但同一批词两张牌都在",
            boards.flatMap { it.left }.map { it.wordId }.toSet(),
            boards.flatMap { it.right }.map { it.wordId }.toSet(),
        )
    }

    @Test
    fun theSameSeedPutsTheSameCardsOnTheTable() {
        val first = MatchBoards.boards(pairs(9), seed = 11)
        val second = MatchBoards.boards(pairs(9), seed = 11)

        assertEquals(first, second)
    }

    @Test
    fun fewerThanTwoPairsIsNoGameAtAll() {
        assertTrue(MatchBoards.boards(emptyList(), seed = 1).isEmpty())
        assertTrue(MatchBoards.boards(pairs(1), seed = 1).isEmpty())
    }

    @Test
    fun aBoardIsClearedWhenItsOwnPairsAreAllMatchedNotTheWholeGroup() {
        val group = MatchBoards.boards(pairs(6), seed = 5)
        val first = group.first()
        val second = group[1]
        val firstIds = first.left.map { it.wordId }.toSet()
        val secondIds = second.left.map { it.wordId }.toSet()

        assertEquals("6 对分两屏", 2, group.size)
        assertTrue("连了别的屏的牌，这一屏还没连完", !boardCleared(first, secondIds))
        assertTrue("差一对也不算连完", !boardCleared(first, firstIds - firstIds.first()))
        assertTrue("这一屏自己的每一对都连上就翻屏", boardCleared(first, firstIds))
        assertTrue("整组都连上了当然算", boardCleared(first, firstIds + secondIds))
        assertTrue("空屏不算连完", !boardCleared(MatchBoard(emptyList(), emptyList()), emptySet()))
    }
}

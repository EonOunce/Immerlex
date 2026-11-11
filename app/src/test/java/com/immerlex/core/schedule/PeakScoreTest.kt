package com.immerlex.core.schedule

import com.immerlex.core.db.LOG_SOURCE_QUIZ
import com.immerlex.core.db.LOG_SOURCE_READER
import com.immerlex.core.db.RecallEventRow
import com.immerlex.core.fsrs.Rating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeakScoreTest {

    private fun event(
        wordId: Long = 1,
        rating: Rating = Rating.GOOD,
        difficulty: Double = 5.0,
        scheduledDays: Int = 0,
        source: String = "quiz",
        epochSec: Long = 0,
    ) = RecallEventRow(
        wordId = wordId,
        rating = rating.value,
        source = source,
        difficulty = difficulty,
        scheduledDays = scheduledDays,
        epochSec = epochSec,
    )

    @Test
    fun nothingReplayedIsJustTheStartingLine() {
        val score = PeakScoreReplay.replay(emptyList())

        assertEquals(1200, score.current)
        assertEquals(1200, score.best)
        assertFalse("零记录不该被叫作巅峰", score.usable)
    }

    @Test
    fun aPlainWordRecalledOnTimeIsAWorthwhileGain() {
        // 对手正好等于自己（默认难度、没攒间隔），期望胜率 0.5，K=24 → 一次 +12。
        val score = PeakScoreReplay.replay(listOf(event()))

        assertEquals(1212, score.current)
        assertEquals(1212, score.best)
    }

    @Test
    fun thePeakSurvivesTheFallAfterIt() {
        val score = PeakScoreReplay.replay(
            listOf(event(wordId = 1), event(wordId = 2, rating = Rating.AGAIN)),
        )

        assertEquals("最高点记的是曾经到过的地方，不是现在", 1212, score.best)
        assertTrue("刚答错一次，当前分要落回最高点下面", score.current < score.best)
    }

    @Test
    fun aHarderWordPaysMoreWhenYouGetIt() {
        val easy = PeakScoreReplay.replay(listOf(event(difficulty = 3.0, scheduledDays = 2)))
        val hard = PeakScoreReplay.replay(listOf(event(difficulty = 9.0, scheduledDays = 2)))

        assertTrue("同样的答对，难的词更值钱", hard.current > easy.current)
        assertTrue(easy.current > 1200 && hard.current > 1200)
    }

    @Test
    fun failingOnAHardWordCostsLessThanFailingOnAnEasyOne() {
        val easy = PeakScoreReplay.replay(listOf(event(difficulty = 3.0, rating = Rating.AGAIN)))
        val hard = PeakScoreReplay.replay(listOf(event(difficulty = 9.0, rating = Rating.AGAIN)))

        assertTrue("答对难词涨得多，答错难词也就摔得少", hard.current > easy.current)
    }

    @Test
    fun aLongIntervalCountsAsAHarderOpponentButOnlyUpToTheCap() {
        val capped = PeakScoreReplay.replay(
            listOf(event(scheduledDays = PeakScoreReplay.MAX_INTERVAL_DAYS)),
        )
        val beyond = PeakScoreReplay.replay(listOf(event(scheduledDays = 9_000)))
        val short = PeakScoreReplay.replay(listOf(event(scheduledDays = 3)))

        assertEquals("一年才回一次的词不该把分推到天上", capped.current, beyond.current)
        assertTrue(capped.current > short.current)
    }

    @Test
    fun hardCountsAsHalfASuccess() {
        val again = PeakScoreReplay.replay(listOf(event(rating = Rating.AGAIN)))
        val hard = PeakScoreReplay.replay(listOf(event(rating = Rating.HARD)))
        val good = PeakScoreReplay.replay(listOf(event(rating = Rating.GOOD)))

        assertTrue("「困难」是想起来了但费劲，不该和答错一样扣", hard.current > again.current)
        assertTrue(hard.current < good.current)
    }

    @Test
    fun aStreakOfSuccessClimbsButEachStepGetsSmaller() {
        fun climb(rounds: Int) =
            PeakScoreReplay.replay(List(rounds) { index -> event(wordId = index.toLong()) }).current
        val scores = (1..10).map { climb(it) }
        val gains = scores.zipWithNext().map { (a, b) -> b - a }

        assertTrue(scores.zipWithNext().all { (a, b) -> b > a })
        assertEquals(1212, scores.first())
        assertTrue("同一个 1200 分的对手，越往上期望胜率越高，涨得就越慢", gains.last() < gains.first())
    }

    @Test
    fun theScoreNeedsEnoughHistoryToBeCalledAPeak() {
        assertFalse(PeakScoreReplay.replay(List(PeakScoreReplay.MIN_SAMPLES - 1) { event(wordId = it.toLong()) }).usable)
        assertTrue(PeakScoreReplay.replay(List(PeakScoreReplay.MIN_SAMPLES) { event(wordId = it.toLong()) }).usable)
    }

    @Test
    fun theTrajectoryEndsWhereTheReplayDoes() {
        val events = List(6) { index -> event(wordId = index.toLong(), scheduledDays = index) }
        val steps = PeakScoreReplay.trajectory(events)

        assertEquals(events.size, steps.size)
        assertEquals("按周折起来的每一步，最后要落在同一个数上", PeakScoreReplay.replay(events).current, steps.last())
    }

    @Test
    fun aGroupIsChargedOnlyForItsOwnRecalls() {
        val before = List(PeakScoreReplay.MIN_SAMPLES) { index -> event(wordId = index.toLong(), epochSec = 100L + index) }
        // 这一组的时间窗里还夹着一笔读小说记的账：两边都有，减下来正好抵消。
        val inWindow = before +
            event(wordId = 90, rating = Rating.AGAIN, source = LOG_SOURCE_QUIZ, epochSec = 300L) +
            event(wordId = 91, source = LOG_SOURCE_READER, epochSec = 310L)

        val change = PeakScoreReplay.groupChange(inWindow, startedAtSec = 200L)
        val expected = PeakScoreReplay.replay(inWindow).current -
            PeakScoreReplay.replay(before + event(wordId = 91, source = LOG_SOURCE_READER, epochSec = 310L)).current

        assertEquals("只把这一组自己那笔记分的题算在它头上", expected, change)
    }

    @Test
    fun aGroupThatWroteNoRealRecallChangesNothing() {
        val all = List(PeakScoreReplay.MIN_SAMPLES) { index -> event(wordId = index.toLong(), epochSec = 100L + index) }

        assertEquals(
            "四选一全攒着当 attempt，库里没多一条真记账，这一组就是一个字都没动",
            0,
            PeakScoreReplay.groupChange(all, startedAtSec = 500L),
        )
    }

    @Test
    fun aGroupSaysNothingWhenTheScoreOrTheStartIsMissing() {
        val thin = List(PeakScoreReplay.MIN_SAMPLES - 2) { index -> event(wordId = index.toLong(), epochSec = 100L + index) } +
            event(wordId = 90, epochSec = 300L)
        val enough = List(PeakScoreReplay.MIN_SAMPLES) { index -> event(wordId = index.toLong(), epochSec = 100L + index) } +
            event(wordId = 90, epochSec = 300L)

        assertNull("记录还不够多，这个分本身就没有意义", PeakScoreReplay.groupChange(thin, startedAtSec = 200L))
        assertNull("升级前留下的会话没记开始时刻，不猜一个数顶上", PeakScoreReplay.groupChange(enough, startedAtSec = 0L))
    }
}

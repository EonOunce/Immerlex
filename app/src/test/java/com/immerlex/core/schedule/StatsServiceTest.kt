package com.immerlex.core.schedule

import com.immerlex.core.db.FSRS_STATE_REVIEW
import com.immerlex.core.db.RecallEventRow
import com.immerlex.core.db.RecallSnapshotEntity
import com.immerlex.core.db.WordEntity
import com.immerlex.core.fsrs.Rating
import com.immerlex.core.fsrs.Scheduler
import com.immerlex.testsupport.FakeDailyLoopDao
import com.immerlex.testsupport.FakeRecallSnapshotDao
import com.immerlex.testsupport.FakeReviewHistoryDao
import com.immerlex.testsupport.FakeReviewLogDao
import com.immerlex.testsupport.FakeWordDao
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 战绩页那几个数的口径（docs/adr/0030）：卡片此刻算出来的、整段历史重放出来的，
 * 加上一天一份的影子。这一段全部只读，唯一往库里落笔的就是那份影子。
 */
class StatsServiceTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val todayDate = LocalDate.of(2026, 10, 28)
    private val today = todayDate.toEpochDay()
    private val now = todayDate.atTime(12, 0).atZone(zone).toInstant()

    private inner class Ctx {
        val words = FakeWordDao()
        val history = FakeReviewHistoryDao()
        val snapshots = FakeRecallSnapshotDao()
        val service = StatsService(
            wordDao = words,
            historyDao = history,
            snapshotDao = snapshots,
            scheduler = Scheduler(
                learningSteps = emptyList(),
                relearningSteps = emptyList(),
                maximumInterval = 3650,
                enableFuzzing = false,
            ),
            boundary = DayBoundary(FakeDailyLoopDao(), FakeReviewLogDao(), zone),
        )
    }

    /** 一个已经排进复习的词：间隔取一半多天前见过它，于是还剩九成上下的回忆把握。 */
    private fun studied(
        word: String,
        stability: Double,
        intervalDays: Int,
        lapses: Int = 0,
        gloss: String = "",
    ): WordEntity {
        val seenDaysAgo = intervalDays / 2L + 1
        return WordEntity(
            word = word,
            translation = gloss,
            source = "test",
            createdAtSec = 0,
            stability = stability,
            difficulty = 5.0,
            dueEpochSec = now.plus(intervalDays - seenDaysAgo, ChronoUnit.DAYS).epochSecond,
            lastReviewEpochSec = now.minus(seenDaysAgo, ChronoUnit.DAYS).epochSecond,
            fsrsState = FSRS_STATE_REVIEW,
            reps = 1,
            lapses = lapses,
            intervalDays = intervalDays,
        )
    }

    private fun event(wordId: Long, rating: Rating, scheduledDays: Int = 0, daysAgo: Long = 0) =
        RecallEventRow(
            wordId,
            rating.value,
            "test",
            5.0,
            scheduledDays,
            now.minus(daysAgo, ChronoUnit.DAYS).epochSecond,
        )

    @Test
    fun anEmptyNotebookStillHasAName() = runBlocking {
        val report = Ctx().service.report(now)

        assertEquals(0, report.totalWords)
        assertEquals("一个词都没学过，也该有个起点称号", RankTier.WHITE, report.rank.tier)
        assertEquals(0, report.peak.samples)
        assertFalse(report.peak.usable)
        assertTrue(report.rivals.isEmpty())
        assertNull("还没打过，两边都没有", report.duel.thisWeek)
        assertNull(report.duel.lastWeek)
    }

    @Test
    fun learnedWordsOnlyCountIntoEveryGauge() = runBlocking {
        val ctx = Ctx()
        // 没学过的生词：stability=null，一个口径都不进。
        ctx.words.insert(WordEntity(word = "newword", source = "test", createdAtSec = 0))
        ctx.words.insert(studied("learned", stability = 2.0, intervalDays = 2))
        ctx.words.insert(studied("mastered", stability = 40.0, intervalDays = 40))
        ctx.words.insert(studied("nemesis", stability = 1.0, intervalDays = 1, lapses = 7, gloss = "宿敌"))

        val report = ctx.service.report(now)

        assertEquals(4, report.totalWords)
        assertEquals("生词不算进已学", 3, report.learnedWords)
        assertEquals("只有间隔 ≥21 的那个算已掌握", 1, report.mastered)
        assertEquals("nemesis", report.mostFailedWord)
        assertEquals("宿敌", report.mostFailedGloss)
        assertEquals(7, report.mostFailedLapses)
        assertTrue("可回忆数落在已学词数之内", report.canRecall in 0..3)
        assertEquals("段位认的就是那个已掌握数", RankTier.WHITE, report.rank.tier)
        assertEquals(19, report.rank.toNext)
    }

    @Test
    fun theStatsPageLeavesOneHighWaterPerDay() = runBlocking {
        val ctx = Ctx()
        ctx.words.insert(studied("harbour", stability = 40.0, intervalDays = 40))

        val report = ctx.service.report(now)
        assertEquals("打开战绩页就替今天记下这个数字", report.canRecall, ctx.snapshots.items[today]?.canRecall)

        // 这一天已经记到过更高：后面再打开、哪怕掉下来了，也不该把高点抹平。
        ctx.snapshots.items[today] = RecallSnapshotEntity(today, canRecall = 99, mastered = 40, atSec = 0)
        val later = ctx.service.report(now)

        assertEquals("一天只留最高的一次", 99, ctx.snapshots.items[today]?.canRecall)
        assertEquals("今天这一份不跟自己比", 0, later.bestDay)
    }

    @Test
    fun bestDayComparesAgainstTheDaysBefore() = runBlocking {
        val ctx = Ctx()
        ctx.words.insert(studied("harbour", stability = 40.0, intervalDays = 40))
        ctx.snapshots.items[today - 1] = RecallSnapshotEntity(today - 1, canRecall = 42, mastered = 7, atSec = 0)

        val report = ctx.service.report(now)

        assertEquals("跟以前的自己比，比的是昨天为止的最高", 42, report.bestDay)
        assertTrue(report.canRecall < 42)
    }

    @Test
    fun aRivalIsAWordThatStillOwesOneMoreTry() = runBlocking {
        val ctx = Ctx()
        val lantern = ctx.words.insert(studied("lantern", stability = 3.0, intervalDays = 3))
        val harbour = ctx.words.insert(studied("harbour", stability = 20.0, intervalDays = 20))
        val quay = ctx.words.insert(studied("quay", stability = 4.0, intervalDays = 4))
        val kiln = ctx.words.insert(studied("kiln", stability = 8.0, intervalDays = 8))
        val deleted = 900L
        ctx.history.items += listOf(
            event(lantern, Rating.AGAIN),
            event(harbour, Rating.GOOD, scheduledDays = 6),
            event(quay, Rating.AGAIN),
            event(lantern, Rating.GOOD, scheduledDays = 2),
            event(harbour, Rating.AGAIN),
            event(deleted, Rating.AGAIN),
        )

        val report = ctx.service.report(now)

        assertEquals(
            "最后一次真记账没想起来的才算，已经爬起来过的不算；最近失败的排前面，删掉词的摆不出来",
            listOf(harbour, quay),
            report.rivals.map { it.id },
        )
    }

    @Test
    fun aHandfulOfRecordsIsNotYetAPeak() = runBlocking {
        val ctx = Ctx()
        val harbour = ctx.words.insert(studied("harbour", stability = 20.0, intervalDays = 20))
        ctx.history.items += listOf(
            event(harbour, Rating.GOOD, scheduledDays = 5),
            event(harbour, Rating.AGAIN),
        )

        val report = ctx.service.report(now)

        assertEquals(2, report.peak.samples)
        assertFalse("记录还不够多，界面不该管它叫巅峰", report.peak.usable)
    }

    @Test
    fun theDuelFoldsThatSameReplayIntoWeeks() = runBlocking {
        val ctx = Ctx()
        val harbour = ctx.words.insert(studied("harbour", stability = 20.0, intervalDays = 20))
        // 上周打过两场，这周还没开场。
        ctx.history.items += listOf(
            event(harbour, Rating.GOOD, scheduledDays = 5, daysAgo = 8),
            event(harbour, Rating.GOOD, scheduledDays = 6, daysAgo = 7),
        )

        val lastWeekOnly = ctx.service.report(now)
        assertNotNull(lastWeekOnly.duel.lastWeek)
        assertNull("这周一条没记，就不能说这周打过", lastWeekOnly.duel.thisWeek)
        assertNull("只有一边就没得比，界面那句比上周要等两边都在", lastWeekOnly.duel.change)

        ctx.history.items += event(harbour, Rating.GOOD, scheduledDays = 7, daysAgo = 1)
        val fought = ctx.service.report(now)

        assertNotNull(fought.duel.thisWeek)
        assertTrue("这一周的新账把最高抬上去了", (fought.duel.thisWeek ?: 0) > (fought.duel.lastWeek ?: 0))
        assertEquals(
            "涨跌就是这两周各自最高相减，不另算一套",
            (fought.duel.thisWeek ?: 0) - (fought.duel.lastWeek ?: 0),
            fought.duel.change,
        )
    }
}

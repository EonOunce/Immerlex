package com.immerlex.core.quiz

import com.immerlex.core.db.QuizRunDao
import com.immerlex.core.db.QuizRunEntity
import com.immerlex.core.db.ReviewHistoryDao
import com.immerlex.core.schedule.PeakScoreReplay
import java.time.Instant

/**
 * 做完一组的留底。[asked] 只数计分的题——答错后的当堂重练不计分，也不该混进正确率。
 * [startedAtSec] 是这一组开始出题的时刻：巅峰分的涨跌要把"这一组自己写下的那几笔"
 * 从整段历史里认出来，没有起点就只能猜，猜不出来索性不算。
 */
data class QuizRunRecord(
    val mode: QuizMode,
    val asked: Int,
    val correct: Int,
    val wrong: Int,
    val bestStreak: Int,
    val startedAtSec: Long = 0,
)

/**
 * 「破纪录」比的是同一个入口自己以前最好对几个，所以跨入口不算；这个入口的第一组不算破纪录。
 * [peakChange] 是这一组让巅峰分涨跌了多少，null 表示这一笔说不准（记录还太少，或不知道这组从哪一秒开始）。
 */
data class QuizRunSaved(val isRecord: Boolean, val previousBest: Int?, val peakChange: Int?)

data class QuizRun(
    val mode: QuizMode,
    val finishedAt: Instant,
    val asked: Int,
    val correct: Int,
    val wrong: Int,
    val bestStreak: Int,
    val peakChange: Int?,
)

/**
 * 会话表在结束时整行删掉，"最近测过什么、各组对了几个"就再没地方能查，
 * 破纪录也就无从比——所以做完一组单独留一行，只读不猜。
 */
class QuizRunStore(private val dao: QuizRunDao, private val historyDao: ReviewHistoryDao) {

    suspend fun save(record: QuizRunRecord, now: Instant): QuizRunSaved {
        val previousBest = dao.bestCorrect(record.mode.name)
        val peakChange = PeakScoreReplay.groupChange(historyDao.recallEvents(), record.startedAtSec)
        dao.insert(
            QuizRunEntity(
                mode = record.mode.name,
                finishedAtSec = now.epochSecond,
                asked = record.asked,
                correct = record.correct,
                wrong = record.wrong,
                bestStreak = record.bestStreak,
                peakChange = peakChange,
            ),
        )
        return QuizRunSaved(
            isRecord = previousBest != null && record.correct > previousBest,
            previousBest = previousBest,
            peakChange = peakChange,
        )
    }

    /** 最近做完的几组。入口名认不出来的整行跳过——不为一条来路不明的记录猜它是哪种测验。 */
    suspend fun recent(limit: Int): List<QuizRun> = dao.recent(limit).mapNotNull { row ->
        val mode = QuizMode.entries.firstOrNull { it.name == row.mode } ?: return@mapNotNull null
        QuizRun(
            mode = mode,
            finishedAt = Instant.ofEpochSecond(row.finishedAtSec),
            asked = row.asked,
            correct = row.correct,
            wrong = row.wrong,
            bestStreak = row.bestStreak,
            peakChange = row.peakChange,
        )
    }
}

package com.immerlex.feature.reader

import com.immerlex.app.R
import com.immerlex.app.ui.UiMessage
import com.immerlex.core.db.LOG_KIND_LEARN
import com.immerlex.core.db.LOG_SOURCE_READER
import com.immerlex.core.db.ReviewLogDao
import com.immerlex.core.db.WordDao
import com.immerlex.core.fsrs.Rating
import com.immerlex.core.reader.ReaderSegment
import com.immerlex.core.reader.ReaderWord
import com.immerlex.core.schedule.ReaderMarks
import com.immerlex.core.schedule.ScheduleService
import com.immerlex.core.schedule.WordAccess
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * 评分之后的三件事：记一笔、把正文颜色按阅读页自己的记录重读一遍、决定撤销按钮还活多久。
 * 状态只有 [state] 这一份，这里只是把阅读页的记账尾巴单独放一处。
 */
internal class ReaderGrading(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<ReaderState>,
    private val messages: MutableSharedFlow<UiMessage>,
    private val wordDao: WordDao,
    private val logDao: ReviewLogDao,
    private val schedule: ScheduleService,
) {
    private val zone: ZoneId = ZoneId.systemDefault()
    private val undoJobs = mutableListOf<Job>()

    fun grade(selected: ReaderWord, access: WordAccess, rating: Rating) {
        scope.launch {
            val entity = selected.wordId?.let { wordDao.findById(it) }
            val result = entity?.let {
                when (access) {
                    WordAccess.Learn -> schedule.learn(it, rating, LOG_SOURCE_READER)
                    WordAccess.Recall -> schedule.recall(it, rating, LOG_SOURCE_READER)
                    WordAccess.Early -> schedule.recall(it, rating, LOG_SOURCE_READER, allowEarly = true)
                    WordAccess.ViewOnly -> null
                }
            }
            if (result == null) {
                closeCard()
                return@launch
            }
            val learnedNow = result.kind == LOG_KIND_LEARN
            val lastOfChapter = isLastWordOfChapter(selected.lookup)
            cancelUndoJobs()
            state.value = state.value.copy(
                bankWords = state.value.bankWords + (result.word.word.lowercase() to result.word),
                learned = state.value.learned + if (learnedNow) 1 else 0,
                recalled = state.value.recalled + if (learnedNow) 0 else 1,
                pendingUndo = UndoPrompt(result.logId, result.kind, result.word.word),
                graded = true,
            )
            refreshMarks()
            // 本篇再没有下一个词卡可以挂撤销按钮，就让这一张多留 5 秒。
            if (!lastOfChapter) closeCard()
            undoJobs += scope.launch {
                delay(ScheduleService.UNDO_WINDOW_SECONDS * MILLIS_PER_SECOND)
                clearUndo()
            }
            if (lastOfChapter) undoJobs += scope.launch {
                delay(UNDO_LINGER_MILLIS)
                closeCard()
            }
        }
    }

    fun undo() {
        val prompt = state.value.pendingUndo ?: return
        cancelUndoJobs()
        // 按钮先撤下：连点两下不会把同一条记录撤两次。
        clearUndo()
        scope.launch {
            val result = schedule.undo(prompt.logId)
            if (result == null) {
                messages.emit(UiMessage(R.string.reader_undo_expired))
                return@launch
            }
            val learnedNow = result.kind == LOG_KIND_LEARN
            state.value = state.value.copy(
                bankWords = state.value.bankWords + (result.word.word.lowercase() to result.word),
                learned = (state.value.learned - if (learnedNow) 1 else 0).coerceAtLeast(0),
                recalled = (state.value.recalled - if (learnedNow) 0 else 1).coerceAtLeast(0),
            )
            refreshMarks()
            messages.emit(UiMessage(R.string.reader_undo_done))
        }
    }

    /** 换一章：旧撤销按钮的定时器作废（按钮本身由 showing() 一起清掉）。 */
    fun cancelTimers() {
        cancelUndoJobs()
    }

    /** 正文的颜色只认阅读页自己写下的记录，所以每次评分与撤销后都重读一遍，不自己加减。 */
    suspend fun refreshMarks() {
        val now = Instant.now()
        val ids = state.value.bankWords.values.map { it.id }
        state.value = state.value.copy(
            marks = ReaderMarks(
                learned = logDao.readerLearnedIdsAmong(ids).toSet(),
                reviewedToday = logDao.readerRecalledIdsSinceAmong(ids, startOfToday(now)).toSet(),
            ),
            marksAtSec = now.epochSecond,
        )
    }

    /** 撤下撤销按钮：窗口过掉了，或者用户已经按了。刚评过分的词卡也跟着收起。 */
    private fun clearUndo() {
        state.value = state.value.copy(pendingUndo = null)
        if (state.value.graded) closeCard()
    }

    private fun cancelUndoJobs() {
        undoJobs.forEach { it.cancel() }
        undoJobs.clear()
    }

    private fun closeCard() {
        state.value = state.value.cardClosed()
    }

    private fun isLastWordOfChapter(lookup: String): Boolean {
        val last = state.value.blocks.flatten().filterIsInstance<ReaderSegment.Word>().lastOrNull()
        return last?.word?.lookup?.trim()?.lowercase() == lookup.trim().lowercase()
    }

    private fun startOfToday(now: Instant): Long =
        now.atZone(zone).toLocalDate().atStartOfDay(zone).toEpochSecond()

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L

        /** 本篇最后一个词的词卡在评分后多留这么久，够伸手点撤销。 */
        const val UNDO_LINGER_MILLIS = 5_000L
    }
}

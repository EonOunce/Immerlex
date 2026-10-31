package com.immerlex.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.immerlex.core.db.ChapterDao
import com.immerlex.core.quiz.QuizService
import com.immerlex.core.roommate.RoommateService
import com.immerlex.core.schedule.DailyLoopService
import com.immerlex.core.schedule.HeatDay
import com.immerlex.core.schedule.ScheduleService
import com.immerlex.core.schedule.TrendDay
import com.immerlex.core.settings.AppPreferences
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeState(
    val loading: Boolean = true,
    val due: Int = 0,
    val newWords: Int = 0,
    val done: Int = 0,
    val streak: Int = 0,
    val checkedIn: Boolean = false,
    val makeupUsedThisWeek: Boolean = false,
    val makeupPending: Boolean = false,
    val hasChapter: Boolean = false,
    val chapterNumber: Int = 0,
    val heat: List<HeatDay> = emptyList(),
    val trend: List<TrendDay> = emptyList(),
    val quizCount: Int = 0,
    val earlyCount: Int = 0,
    val roomMet: Boolean = false,
    val roomName: String = "",
    val roomLine: String = "",
    val roomBalance: Int = 0,
)

/**
 * 今日任务页。数字全部来自 [ScheduleService.todayDueCount] 与 [DailyLoopService]，
 * 也就是生成章节时用的同一份今日队列——首页不许另算一套"还剩几个词"。
 */
class HomeViewModel(
    private val dailyLoop: DailyLoopService,
    private val schedule: ScheduleService,
    private val quiz: QuizService,
    private val chapterDao: ChapterDao,
    private val prefs: AppPreferences,
    private val roommate: RoommateService,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    /** 从阅读页打分回来时要重算：连续天数和"今日已完成"跟着打分走。 */
    fun refresh() = viewModelScope.launch { load() }

    private suspend fun load() {
        val now = Instant.now()
        val limit = prefs.dailyNewLimit
        val queue = schedule.todaysQueue(now, limit)
        val info = dailyLoop.info(now, queue.reviewWords.size + queue.newWords.size)
        val latest = chapterDao.latest(prefs.genre)
        val room = roommate.hook(now)
        _state.value = HomeState(
            loading = false,
            due = info.dueCount,
            newWords = queue.newWords.size,
            done = info.reviewedToday,
            streak = info.streakDays,
            checkedIn = info.checkedInToday,
            makeupUsedThisWeek = info.makeupUsedThisWeek,
            makeupPending = info.makeupPendingToday,
            hasChapter = latest != null,
            chapterNumber = latest?.number ?: 0,
            heat = dailyLoop.heatGrid(now),
            trend = dailyLoop.trend(now),
            // 入口数字与测验自己的候选集合同源：今天到期 + 今天学过但还没回忆的词。
            // 一组最多 20 题，入口显示的就是这一组能测多少，不是候选池总数。
            quizCount = quiz.dueCandidateIds(now, limit).size.coerceAtMost(QuizService.SESSION_LIMIT),
            earlyCount = quiz.earlyCandidates(now).size,
            roomMet = room.met,
            roomName = room.name,
            roomLine = room.line,
            roomBalance = room.balance,
        )
    }
}

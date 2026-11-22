package com.immerlex.feature.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.immerlex.core.quiz.MatchBoard
import com.immerlex.core.quiz.MatchPair
import com.immerlex.core.quiz.QuizMode
import com.immerlex.core.quiz.boardCleared
import com.immerlex.core.quiz.QuizRunRecord
import com.immerlex.core.quiz.QuizRunSaved
import com.immerlex.core.quiz.QuizRunStore
import com.immerlex.core.quiz.QuizService
import com.immerlex.core.settings.AppPreferences
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MatchPhase { LOADING, EMPTY, PLAYING, DONE }

enum class MatchSide { LEFT, RIGHT }

data class MatchState(
    val phase: MatchPhase = MatchPhase.LOADING,
    val boardIndex: Int = 0,
    val boardCount: Int = 0,
    val left: List<MatchPair> = emptyList(),
    val right: List<MatchPair> = emptyList(),
    /** 整组里已经连上的词，跨屏累计：一屏连完就换下一屏，进度也跟着走。 */
    val matched: Set<Long> = emptySet(),
    val pickedSide: MatchSide? = null,
    val pickedId: Long? = null,
    /** 刚连错的那两个词：红一下，随后自动恢复。 */
    val wrongIds: Set<Long> = emptySet(),
    val totalPairs: Int = 0,
    /** 一次就连对的几对：结果页那个大字，也是这一组的得分。 */
    val firstTry: Int = 0,
    /** 配错过的词面：结果页那堵墙。 */
    val missed: List<String> = emptyList(),
    val record: QuizRunSaved? = null,
) {
    val remaining: Int get() = totalPairs - matched.size
}

/**
 * 连连看：左边英文、右边中文，一点一配对。考的和「今日测验」同一批词（今天还没碰过的），
 * 一屏最多 5 对、分几屏由这一段的词数决定。第一次就配对成功的词才写一笔认出（1 分），
 * 配错过的词这一组都不再计分；退出即散，不留存档——它本来就是一气呵成玩完的东西。
 */
class MatchViewModel(
    private val quiz: QuizService,
    private val prefs: AppPreferences,
    private val runs: QuizRunStore,
) : ViewModel() {

    private val _state = MutableStateFlow(MatchState())
    val state: StateFlow<MatchState> = _state.asStateFlow()

    private var started = false
    private var boards: List<MatchBoard> = emptyList()
    /** 这一组里已经配错过的词：之后再配对也不计分。 */
    private val fumbled = HashSet<Long>()
    private var firstTryCount = 0
    private var startedAtSec = 0L
    private var advanceJob: Job? = null
    private var flashJob: Job? = null

    /** 路由进入时调用一次；配置变更后重复调用不能把牌面清掉。 */
    fun start() {
        if (started) return
        started = true
        plan()
    }

    /** 结果页的「再连一组」：配过的词今天已经算碰过，下一组自己换一批。 */
    fun again() {
        if (_state.value.phase != MatchPhase.DONE) return
        plan()
    }

    fun tap(side: MatchSide, wordId: Long) {
        val current = _state.value
        if (current.phase != MatchPhase.PLAYING) return
        if (wordId in current.matched) return
        if (current.pickedSide == null) {
            _state.value = current.copy(pickedSide = side, pickedId = wordId, wrongIds = emptySet())
            return
        }
        if (current.pickedSide == side) {
            val same = current.pickedId == wordId
            _state.value = current.copy(
                pickedSide = if (same) null else side,
                pickedId = if (same) null else wordId,
                wrongIds = emptySet(),
            )
            return
        }
        val picked = current.pickedId ?: return
        if (picked == wordId) match(current, wordId) else miss(current, picked, wordId)
    }

    /** 数字键 6：把刚才点的那张放回去。配错是写不进库的，但这一对在这一组里就不再计分了，所以撤销要趁早。 */
    fun cancelPick() {
        val current = _state.value
        if (current.phase != MatchPhase.PLAYING) return
        _state.value = current.copy(pickedSide = null, pickedId = null, wrongIds = emptySet())
    }

    private fun plan() = viewModelScope.launch {
        advanceJob?.cancel()
        flashJob?.cancel()
        fumbled.clear()
        firstTryCount = 0
        _state.value = MatchState(phase = MatchPhase.LOADING)
        val plan = try {
            withContext(Dispatchers.Default) {
                quiz.planMatch(
                    now = Instant.now(),
                    dailyNewLimit = prefs.dailyNewLimit,
                    segments = prefs.quizSegments,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = MatchState(phase = MatchPhase.EMPTY)
            return@launch
        }
        if (plan.isEmpty) {
            _state.value = MatchState(phase = MatchPhase.EMPTY)
            return@launch
        }
        boards = plan.boards
        startedAtSec = Instant.now().epochSecond
        _state.value = MatchState(
            phase = MatchPhase.PLAYING,
            boardCount = boards.size,
            totalPairs = plan.pairCount,
        )
        showBoard(0)
    }

    private fun showBoard(index: Int) {
        val board = boards[index]
        _state.value = _state.value.copy(
            phase = MatchPhase.PLAYING,
            boardIndex = index,
            boardCount = boards.size,
            left = board.left,
            right = board.right,
            pickedSide = null,
            pickedId = null,
            wrongIds = emptySet(),
        )
    }

    private fun match(current: MatchState, wordId: Long) {
        val firstTry = wordId !in fumbled
        if (firstTry) {
            firstTryCount += 1
            viewModelScope.launch { quiz.recordMatch(wordId, firstTry = true) }
        }
        val matched = current.matched + wordId
        _state.value = current.copy(
            matched = matched,
            pickedSide = null,
            pickedId = null,
            wrongIds = emptySet(),
            firstTry = firstTryCount,
        )
        val board = boards.getOrNull(current.boardIndex)
        if (board != null && boardCleared(board, matched)) {
            // 让最后一对露一下再翻屏：连完立刻跳走，看不出自己连上了什么。
            advanceJob = viewModelScope.launch {
                delay(BOARD_PAUSE_MS)
                nextBoard()
            }
        }
    }

    /** 连错了：两个词都算配错过（这一组不再给它们计分），红闪一下，不写任何记录。 */
    private fun miss(current: MatchState, pickedId: Long, otherId: Long) {
        fumbled += pickedId
        fumbled += otherId
        flashJob?.cancel()
        _state.value = current.copy(
            pickedSide = null,
            pickedId = null,
            wrongIds = setOf(pickedId, otherId),
        )
        flashJob = viewModelScope.launch {
            delay(WRONG_FLASH_MS)
            val now = _state.value
            if (now.wrongIds == setOf(pickedId, otherId)) {
                _state.value = now.copy(wrongIds = emptySet())
            }
        }
    }

    private fun nextBoard() {
        val current = _state.value
        if (current.phase != MatchPhase.PLAYING) return
        val next = current.boardIndex + 1
        if (next < boards.size) showBoard(next) else finish()
    }

    private fun finish() {
        val current = _state.value
        val byId = boards.asSequence().flatMap { it.left.asSequence() }.associateBy { it.wordId }
        _state.value = current.copy(
            phase = MatchPhase.DONE,
            pickedSide = null,
            pickedId = null,
            wrongIds = emptySet(),
            missed = fumbled.mapNotNull { byId[it]?.word }.sortedBy { it.lowercase() },
        )
        val record = QuizRunRecord(
            mode = QuizMode.MATCH,
            asked = current.totalPairs,
            correct = firstTryCount,
            wrong = current.totalPairs - firstTryCount,
            bestStreak = 0,
            startedAtSec = startedAtSec,
        )
        viewModelScope.launch {
            // 留底写完才知道这一组的巅峰涨跌与破纪录：还在结果页才补上去。
            val saved = runs.save(record, Instant.now())
            if (_state.value.phase == MatchPhase.DONE) {
                _state.value = _state.value.copy(record = saved)
            }
        }
    }

    private companion object {
        /** 一屏连完之后停一下再翻屏。 */
        const val BOARD_PAUSE_MS = 650L
        /** 连错的红闪多久。 */
        const val WRONG_FLASH_MS = 420L
    }
}

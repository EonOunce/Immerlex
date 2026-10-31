package com.immerlex.feature.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.immerlex.core.quiz.QuizMode
import com.immerlex.core.quiz.QuizQuestion
import com.immerlex.core.quiz.QuizResume
import com.immerlex.core.quiz.QuizResumeStore
import com.immerlex.core.quiz.QuizService
import com.immerlex.core.quiz.QuizStage
import com.immerlex.core.settings.AppPreferences
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class QuizPhase { LOADING, EMPTY, QUESTION, DONE, ERROR }

data class QuizState(
    val phase: QuizPhase = QuizPhase.LOADING,
    val mode: QuizMode = QuizMode.DUE,
    val stage: QuizStage = QuizStage.COLLECTING,
    /** true 表示当前是答错后的当堂重练：不写记录、不计分。 */
    val practice: Boolean = false,
    val question: QuizQuestion? = null,
    val index: Int = 0,
    val total: Int = 0,
    val chosen: Int? = null,
    val correct: Boolean = false,
    val correctCount: Int = 0,
    val roundTotal: Int = 0,
    val wrongCount: Int = 0,
    val group: Int = 1,
    /** 有答错的词排在末尾重练：最后一题的按钮因此仍写"下一题"，点了不该以为会话结束。 */
    val practiceQueued: Boolean = false,
) {
    val answered: Boolean get() = chosen != null
    val isLast: Boolean get() = total > 0 && index >= total - 1
    val showsFinish: Boolean get() = isLast && !practiceQueued

    /** 练习轮只是当堂重练，答对也不计入本组得分。 */
    fun afterAnswer(index: Int, correct: Boolean): QuizState = copy(
        chosen = index,
        correct = correct,
        correctCount = correctCount + if (correct && !practice) 1 else 0,
    )
}

/**
 * 一次测验会话的状态机：出题 → 逐题作答（提交即记账）→ 答错的词换题型重练（不计分）
 * → 结果页。提前复习在结果页可以"再来一组"。
 * 记账全部交给 [QuizService]，这里只决定"现在给用户看哪一屏"。
 */
class QuizViewModel(
    private val quiz: QuizService,
    private val prefs: AppPreferences,
    private val resume: QuizResumeStore,
) : ViewModel() {

    private val _state = MutableStateFlow(QuizState())
    val state: StateFlow<QuizState> = _state.asStateFlow()

    private var questions: List<QuizQuestion> = emptyList()
    private var alternatives: Map<Long, QuizQuestion> = emptyMap()
    private val wrong = mutableListOf<QuizQuestion>()
    private var practiceQuestions: List<QuizQuestion> = emptyList()
    private var excluded: Set<Long> = emptySet()
    private var started = false
    private val saveLock = Mutex()

    /** 路由进入时调用一次；配置变更后重复调用不能把进度清掉。 */
    fun start(mode: QuizMode) {
        if (started) return
        started = true
        _state.value = QuizState(phase = QuizPhase.LOADING, mode = mode)
        viewModelScope.launch {
            val saved = try {
                resume.load(mode)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 读不到就当没存过：重排一组，不能因为一行坏数据把用户卡在"正在准备题目"。
                null
            }
            if (saved != null && restore(saved)) return@launch
            plan(mode)
        }
    }

    fun answer(index: Int) {
        val current = _state.value
        val question = current.question ?: return
        if (current.answered || index !in question.options.indices) return
        val correct = question.options[index].correct
        _state.value = current.afterAnswer(index, correct)
        if (!current.practice) {
            if (!correct) {
                wrong += question
                if (alternatives.containsKey(question.wordId)) {
                    _state.value = _state.value.copy(practiceQueued = true)
                }
            }
            viewModelScope.launch { quiz.record(question, correct) }
        }
        persist()
    }

    fun next() {
        val current = _state.value
        if (!current.answered) return
        if (current.practice) {
            if (!current.isLast) showPractice(current.index + 1) else finish()
            return
        }
        if (!current.isLast) {
            _state.value = current.copy(
                question = questions[current.index + 1],
                index = current.index + 1,
                chosen = null,
                correct = false,
            )
            persist()
            return
        }
        startPracticeOrFinish()
    }

    /** 提前复习结果页的"再来一组"：避开刚考过的词；到期组也用它把剩余的到期词分批测完。 */
    fun again() {
        val current = _state.value
        if (current.phase != QuizPhase.DONE) return
        excluded = excluded + questions.map { it.wordId }
        _state.value = current.copy(
            phase = QuizPhase.LOADING,
            stage = QuizStage.COLLECTING,
            group = current.group + 1,
            question = null,
            index = 0,
            total = 0,
            chosen = null,
            correct = false,
            correctCount = 0,
            roundTotal = 0,
            wrongCount = 0,
            practice = false,
        )
        plan(current.mode)
    }

    /** 出题失败后重试同一组；不能把用户永远停在"正在准备题目"。 */
    fun retry() {
        val current = _state.value
        if (current.phase != QuizPhase.ERROR) return
        _state.value = QuizState(
            phase = QuizPhase.LOADING,
            mode = current.mode,
            stage = QuizStage.COLLECTING,
            group = current.group,
        )
        plan(current.mode)
    }

    private fun plan(mode: QuizMode) = viewModelScope.launch {
        val session = try {
            withContext(Dispatchers.Default) {
                val now = Instant.now()
                when (mode) {
                    QuizMode.DUE -> quiz.planDue(
                        now = now,
                        dailyNewLimit = prefs.dailyNewLimit,
                        onStage = ::publishStage,
                    )
                    QuizMode.EARLY -> quiz.planEarly(
                        now = now,
                        excludeWordIds = excluded,
                        onStage = ::publishStage,
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = _state.value.copy(phase = QuizPhase.ERROR, question = null)
            return@launch
        }
        if (session.isEmpty) {
            _state.value = _state.value.copy(phase = QuizPhase.EMPTY, question = null)
            clearSaved(mode)
            return@launch
        }
        questions = session.questions
        alternatives = session.alternatives
        practiceQuestions = emptyList()
        wrong.clear()
        _state.value = QuizState(
            phase = QuizPhase.QUESTION,
            mode = mode,
            question = questions.first(),
            total = questions.size,
            roundTotal = questions.size,
            group = _state.value.group,
        )
        persist()
    }

    private fun publishStage(stage: QuizStage) {
        _state.value = _state.value.copy(stage = stage)
    }

    private fun startPracticeOrFinish() {
        val practice = wrong.mapNotNull { alternatives[it.wordId] }
        if (practice.isEmpty()) {
            finish()
            return
        }
        practiceQuestions = practice
        _state.value = _state.value.copy(
            phase = QuizPhase.QUESTION,
            practice = true,
            question = practice.first(),
            index = 0,
            total = practice.size,
            chosen = null,
            correct = false,
            wrongCount = wrong.size,
            practiceQueued = false,
        )
        persist()
    }

    private fun showPractice(index: Int) {
        _state.value = _state.value.copy(
            question = practiceQuestions[index],
            index = index,
            chosen = null,
            correct = false,
        )
        persist()
    }

    private fun finish() {
        _state.value = _state.value.copy(
            phase = QuizPhase.DONE,
            question = null,
            practice = false,
            wrongCount = wrong.size,
        )
        clearSaved(_state.value.mode)
    }

    /** 回到上次没做完的那一组：题目与进度原样复原，作答记录早已写过，不重考不重记。 */
    private fun restore(saved: QuizResume): Boolean {
        if (saved.questions.isEmpty()) return false
        questions = saved.questions
        alternatives = saved.alternatives
        practiceQuestions = if (saved.inPractice) {
            saved.wrongIds.mapNotNull { alternatives[it] }
        } else {
            emptyList()
        }
        wrong.clear()
        wrong += saved.wrongIds.mapNotNull { id -> saved.questions.firstOrNull { it.wordId == id } }
        excluded = saved.excludedIds
        val current = if (saved.inPractice) practiceQuestions[saved.index] else questions[saved.index]
        _state.value = QuizState(
            phase = QuizPhase.QUESTION,
            mode = saved.mode,
            question = current,
            index = saved.index,
            total = if (saved.inPractice) practiceQuestions.size else questions.size,
            chosen = saved.chosen,
            correct = saved.correct,
            correctCount = saved.correctCount,
            roundTotal = questions.size,
            wrongCount = wrong.size,
            group = saved.group,
            practice = saved.inPractice,
            practiceQueued = !saved.inPractice && wrong.any { alternatives.containsKey(it.wordId) },
        )
        return true
    }

    private fun snapshot(): QuizResume? {
        val current = _state.value
        if (current.phase != QuizPhase.QUESTION) return null
        return QuizResume(
            mode = current.mode,
            questions = questions,
            alternatives = alternatives,
            wrongIds = wrong.map { it.wordId },
            excludedIds = excluded,
            index = current.index,
            inPractice = current.practice,
            chosen = current.chosen,
            correct = current.correct,
            correctCount = current.correctCount,
            group = current.group,
        )
    }

    /** 按提问顺序排队写库：后一次状态一定盖住前一次，不会出现旧进度把新进度写回去。 */
    private fun persist() {
        val saved = snapshot() ?: return
        viewModelScope.launch { saveLock.withLock { resume.save(saved) } }
    }

    private fun clearSaved(mode: QuizMode) {
        viewModelScope.launch { saveLock.withLock { resume.clear(mode) } }
    }
}

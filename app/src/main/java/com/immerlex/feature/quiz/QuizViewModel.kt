package com.immerlex.feature.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.immerlex.core.quiz.QuizMode
import com.immerlex.core.quiz.QuizQuestion
import com.immerlex.core.quiz.QuizService
import com.immerlex.core.settings.AppPreferences
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class QuizPhase { LOADING, EMPTY, QUESTION, DONE }

data class QuizState(
    val phase: QuizPhase = QuizPhase.LOADING,
    val mode: QuizMode = QuizMode.DUE,
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
) : ViewModel() {

    private val _state = MutableStateFlow(QuizState())
    val state: StateFlow<QuizState> = _state.asStateFlow()

    private var questions: List<QuizQuestion> = emptyList()
    private var alternatives: Map<Long, QuizQuestion> = emptyMap()
    private val wrong = mutableListOf<QuizQuestion>()
    private var practiceQuestions: List<QuizQuestion> = emptyList()
    private var excluded: Set<Long> = emptySet()
    private var started = false

    /** 路由进入时调用一次；配置变更后重复调用不能把进度清掉。 */
    fun start(mode: QuizMode) {
        if (started) return
        started = true
        _state.value = QuizState(phase = QuizPhase.LOADING, mode = mode)
        plan(mode)
    }

    fun answer(index: Int) {
        val current = _state.value
        val question = current.question ?: return
        if (current.answered || index !in question.options.indices) return
        val correct = question.options[index].correct
        _state.value = current.afterAnswer(index, correct)
        if (current.practice) return
        if (!correct) {
            wrong += question
            if (alternatives.containsKey(question.wordId)) {
                _state.value = _state.value.copy(practiceQueued = true)
            }
        }
        viewModelScope.launch { quiz.record(question, correct) }
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
            return
        }
        startPracticeOrFinish()
    }

    /** 提前复习结果页的"再来一组"：避开刚考过的词，再从今天碰过的词里取 5 个。 */
    fun again() {
        val current = _state.value
        if (current.phase != QuizPhase.DONE || current.mode != QuizMode.EARLY) return
        excluded = excluded + questions.map { it.wordId }
        _state.value = current.copy(
            phase = QuizPhase.LOADING,
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
        plan(QuizMode.EARLY)
    }

    private fun plan(mode: QuizMode) = viewModelScope.launch {
        val session = withContext(Dispatchers.Default) {
            val now = Instant.now()
            when (mode) {
                QuizMode.DUE -> quiz.planDue(now, prefs.dailyNewLimit)
                QuizMode.EARLY -> quiz.planEarly(now, excluded)
            }
        }
        if (session.isEmpty) {
            _state.value = _state.value.copy(phase = QuizPhase.EMPTY, question = null)
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
    }

    private fun showPractice(index: Int) {
        _state.value = _state.value.copy(
            question = practiceQuestions[index],
            index = index,
            chosen = null,
            correct = false,
        )
    }

    private fun finish() {
        _state.value = _state.value.copy(
            phase = QuizPhase.DONE,
            question = null,
            practice = false,
            wrongCount = wrong.size,
        )
    }
}

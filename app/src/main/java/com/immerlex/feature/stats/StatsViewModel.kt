package com.immerlex.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.immerlex.core.quiz.QuizRun
import com.immerlex.core.quiz.QuizRunStore
import com.immerlex.core.schedule.RetentionReport
import com.immerlex.core.schedule.StatsService
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 战绩页：只把已存的数据读成一份报告，不改任何排期。
 * 回到这一栏时重算一次，用户刚打完分就能看到"此刻能想起几个词"跟着变。
 */
class StatsViewModel(
    private val stats: StatsService,
    private val runs: QuizRunStore,
) : ViewModel() {

    private val _state = MutableStateFlow(StatsUi())
    val state: StateFlow<StatsUi> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        _state.value = StatsUi(
            loading = false,
            report = stats.report(Instant.now()),
            runs = runs.recent(RECENT_RUNS),
        )
    }

    private companion object {
        const val RECENT_RUNS = 5
    }
}

data class StatsUi(
    val loading: Boolean = true,
    val report: RetentionReport = RetentionReport.blank(),
    val runs: List<QuizRun> = emptyList(),
)

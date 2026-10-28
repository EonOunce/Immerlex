package com.immerlex.core.notify

import android.content.Context
import com.immerlex.core.ai.GenerateOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 写作超过 [PROGRESS_AFTER_MS] 才挂通知，写完就地换成结果。
 * 十秒内完成的什么都不发：那种情况下用户还盯着屏幕，snackbar 已经说过了。
 */
class GenerationNotifier(private val context: Context, private val scope: CoroutineScope) {

    private var timer: Job? = null

    @Volatile
    private var posted = false

    /** 包住一次生成：到点挂"进行中"，结束换成结果，中途被取消就把它撤掉。 */
    suspend fun runTracked(block: suspend () -> GenerateOutcome): GenerateOutcome {
        begin()
        var settled = false
        try {
            val outcome = block()
            settled = true
            finish(outcome)
            return outcome
        } finally {
            if (!settled) abort()
        }
    }

    private fun begin() {
        timer?.cancel()
        posted = false
        timer = scope.launch {
            delay(PROGRESS_AFTER_MS)
            Notifier.ensureChannels(context)
            Notifier.postWriting(context)
            posted = true
        }
    }

    private fun finish(outcome: GenerateOutcome) {
        stopTimer()
        if (!posted) return
        posted = false
        when (outcome) {
            is GenerateOutcome.Saved ->
                Notifier.postWritten(context, outcome.chapter.number, outcome.chapter.title)

            is GenerateOutcome.Rejected, is GenerateOutcome.Failed -> Notifier.postWriteFailed(context)

            is GenerateOutcome.NoWords -> Notifier.cancelWriting(context)
        }
    }

    /** 协程被取消（用户划掉页面）：不能让"进行中"那条一直挂着转圈。 */
    private fun abort() {
        stopTimer()
        if (posted) {
            posted = false
            Notifier.cancelWriting(context)
        }
    }

    private fun stopTimer() {
        timer?.cancel()
        timer = null
    }

    companion object {
        const val PROGRESS_AFTER_MS = 10_000L
    }
}

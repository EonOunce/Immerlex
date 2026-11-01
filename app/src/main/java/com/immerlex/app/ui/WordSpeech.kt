package com.immerlex.app.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.immerlex.app.R
import java.util.Locale

/** 朗读失败要能说清缺的是引擎还是英文语音包，否则用户只看到"点了没反应"。 */
enum class SpeechProblem { None, NoEngine, NoEnglish, Failed }

/**
 * 单词朗读。阅读页的词卡和测验的题干都要读，所以它不住在任何一个 feature 里。
 * 没有引擎或没有英文语音包时不报错、只是不出声，界面自己决定要不要提一句。
 */
class WordSpeaker {
    private var engine: TextToSpeech? = null
    private var pending: String? = null

    var ready by mutableStateOf(false)
        private set
    var problem by mutableStateOf(SpeechProblem.None)
        private set

    fun start(context: Context) {
        if (engine != null) return
        engine = TextToSpeech(context.applicationContext) { status -> onInit(status) }
    }

    private fun onInit(status: Int) {
        val tts = engine
        if (status != TextToSpeech.SUCCESS || tts == null) {
            problem = SpeechProblem.NoEngine
            pending = null
            return
        }
        val locale = VOICE_LOCALES.firstOrNull { tts.setLanguage(it) >= TextToSpeech.LANG_AVAILABLE }
        if (locale == null) {
            problem = SpeechProblem.NoEnglish
            pending = null
            return
        }
        ready = true
        problem = SpeechProblem.None
        pending?.let { speakNow(it) }
        pending = null
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        if (!ready) {
            // 引擎还在初始化时点的朗读，等它就绪补读，不让用户再点一次。
            if (problem == SpeechProblem.None) pending = text
            return
        }
        speakNow(text)
    }

    private fun speakNow(text: String) {
        val result = engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        if (result != TextToSpeech.SUCCESS) problem = SpeechProblem.Failed
    }

    fun shutdown() {
        engine?.stop()
        engine?.shutdown()
        engine = null
        ready = false
    }

    private companion object {
        const val UTTERANCE_ID = "immerlex-word"
        val VOICE_LOCALES = listOf(Locale.US, Locale.UK, Locale.ENGLISH)
    }
}

@Composable
fun rememberWordSpeaker(): WordSpeaker {
    val context = LocalContext.current.applicationContext
    val speaker = remember(context) { WordSpeaker() }
    DisposableEffect(speaker) {
        speaker.start(context)
        onDispose { speaker.shutdown() }
    }
    return speaker
}

fun speechProblemText(problem: SpeechProblem): Int = when (problem) {
    SpeechProblem.NoEngine -> R.string.reader_speak_no_engine
    SpeechProblem.NoEnglish -> R.string.reader_speak_no_english
    else -> R.string.reader_speak_failed
}

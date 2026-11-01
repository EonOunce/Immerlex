package com.immerlex.feature.quiz

import androidx.annotation.StringRes
import com.immerlex.app.R
import com.immerlex.core.quiz.QuizStage
import com.immerlex.core.quiz.QuizType

@StringRes
internal fun typeLabel(type: QuizType): Int = when (type) {
    QuizType.EN_ZH -> R.string.quiz_type_en_zh
    QuizType.ZH_EN -> R.string.quiz_type_zh_en
    QuizType.CLOZE -> R.string.quiz_type_cloze
    QuizType.DEFINITION -> R.string.quiz_type_definition
    QuizType.COLLOCATION -> R.string.quiz_type_collocation
}

@StringRes
internal fun stageLabel(stage: QuizStage): Int = when (stage) {
    QuizStage.COLLECTING -> R.string.quiz_stage_words
    QuizStage.READING_LOGS -> R.string.quiz_stage_records
    QuizStage.ASKING_AI -> R.string.quiz_stage_ai
    QuizStage.BUILDING -> R.string.quiz_stage_build
}

@StringRes
internal fun promptLabel(type: QuizType): Int = when (type) {
    QuizType.EN_ZH -> R.string.quiz_prompt_en_zh
    QuizType.ZH_EN -> R.string.quiz_prompt_zh_en
    QuizType.CLOZE -> R.string.quiz_prompt_cloze
    QuizType.DEFINITION -> R.string.quiz_prompt_definition
    QuizType.COLLOCATION -> R.string.quiz_prompt_collocation
}

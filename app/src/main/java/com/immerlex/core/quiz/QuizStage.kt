package com.immerlex.core.quiz

/** 出题过程中用户能看见的阶段；AI 慢的时候至少知道卡在哪一步。 */
enum class QuizStage { COLLECTING, READING_LOGS, ASKING_AI, BUILDING }
